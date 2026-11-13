package com.cosmos.cosmos_backend.approach.service;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.config.EvaluationWorkerProperties;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.domain.EvaluationFailureCode;
import com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus;
import com.cosmos.cosmos_backend.approach.domain.KeywordResult;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import com.cosmos.cosmos_backend.problem.domain.entity.Keyword;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.repository.KeywordRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.RunningLimitRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평가 워커가 쓰는 트랜잭션 단위 모음. AI 호출은 트랜잭션 밖에서 하므로 여기에 넣지 않음. */
@Service
@RequiredArgsConstructor
public class SolutionEvaluationExecutionService {

    /** AI 호출에 필요한 입력 (요청 본문 + 결과 병합에 쓸 우리 키워드 목록). */
    public record EvaluationInput(AiEvaluationRequest request, List<Keyword> keywords) {
    }

    /** 선점한 작업과 이번 시도 번호. */
    public record ClaimedTask(Long taskId, int attempt) {
    }

    private final SolutionEvaluationTaskRepository taskRepository;
    private final ProblemRepository problemRepository;
    private final KeywordRepository keywordRepository;
    private final RunningLimitRepository runningLimitRepository;
    private final ApproachSubmissionRepository approachSubmissionRepository;
    private final ApproachSubmissionService approachSubmissionService;
    private final NotificationService notificationService;
    private final EvaluationWorkerProperties properties;
    private final Clock clock;

    /** 작업을 선점해 PROCESSING으로 바꾸고 작업 id와 시도 번호를 반환. 처리할 작업이 없으면 빈 값. */
    @Transactional
    public Optional<ClaimedTask> claimNext() {
        // 1. 대기 중이거나 재시도 시각이 지난 작업을 잠그고 조회
        Optional<SolutionEvaluationTask> next = taskRepository.findNextForUpdate(now());
        if (next.isEmpty()) {
            return Optional.empty();
        }

        // 2. 처리 중으로 바꿈 (시도 횟수가 1 늘고, 커밋 때 UPDATE가 나가면서 잠금이 풀림)
        SolutionEvaluationTask task = next.get();
        task.startProcessing(now());
        return Optional.of(new ClaimedTask(task.getId(), task.getWorkerAttemptCount()));
    }

    /** AI 호출 전 준비. 문제·키워드·실행 제한을 조회해 AI 요청을 조립. */
    @Transactional(readOnly = true)
    public EvaluationInput prepare(Long taskId) {
        // 1. 작업과 문제를 조회
        SolutionEvaluationTask task = findTask(taskId);
        Problem problem = findProblem(task.getProblemId());

        // 2. 키워드·실행 제한을 조회해 AI 요청을 조립
        List<Keyword> keywords = keywordRepository.findByProblemIdOrderById(task.getProblemId());
        AiEvaluationRequest request = ApproachSubmissionService.buildAiRequest(
                problem, task.getNaturalSolution(), keywords, runningLimitRepository.findByProblemId(task.getProblemId()));
        return new EvaluationInput(request, keywords);
    }

    /** AI 결과를 제출 행에 반영하고 작업을 SUCCEEDED로 바꿈. 모두 한 트랜잭션. */
    @Transactional
    public void complete(Long taskId, int attempt, List<Keyword> keywords, AiEvaluationResult result) {
        // 0. 작업을 잠그고, 그 사이 복구되어 다른 시도로 넘어갔다면 이 결과는 버림
        SolutionEvaluationTask task = lockTask(taskId);
        if (!isCurrentAttempt(task, attempt)) {
            throw new IllegalStateException("stale evaluation result: taskId=" + taskId + ", attempt=" + attempt);
        }

        // 1. 문제를 조회
        Problem problem = findProblem(task.getProblemId());
        Long userId = task.getUserId();
        Long problemId = task.getProblemId();
        Category category = task.getSelectedCategory();

        // 2. AI 판정을 우리 키워드 목록 기준으로 병합하고 저장용 형태로 변환
        List<KeywordResult> keywordResults = ApproachSubmissionService.mergeKeywordJudgements(keywords, result.keywords()).stream()
                .map(keyword -> new KeywordResult(keyword.keyword(), keyword.included()))
                .toList();

        // 3. 선택한 카테고리와 문제의 정답 카테고리를 비교
        boolean categoryResult = category.equals(problem.getCategory());

        // 4. 제출 행 반영: 없으면 락 없이 새로 저장, 있으면 잠그고 재제출 처리
        ApproachSubmission submission;
        boolean alreadySolved;
        if (approachSubmissionRepository.findByUserIdAndProblemId(userId, problemId).isEmpty()) {
            ApproachSubmission created = new ApproachSubmission(userId, problemId, category, task.getNaturalSolution(),
                    categoryResult, result.score(), result.feedback(), task.getCreatedAt());
            created.updateKeywordResults(keywordResults);
            submission = approachSubmissionRepository.save(created);
            alreadySolved = false;
        } else {
            ApproachSubmission existing = approachSubmissionRepository
                    .findForUpdateByUserIdAndProblemId(userId, problemId).orElseThrow();

            // 잠근 뒤 한도를 다시 확인
            if (existing.getSubmittedCount() >= ApproachSubmission.MAX_SUBMISSION_COUNT) {
                throw ApproachSubmissionService.limitExceeded(problemId, existing.getSubmittedCount());
            }

            alreadySolved = existing.getIsSolved();
            existing.resubmit(category, task.getNaturalSolution(), categoryResult,
                    result.score(), result.feedback(), task.getCreatedAt());
            existing.updateKeywordResults(keywordResults);
            submission = existing;
        }

        // 5. 첫 정답이면 풀이 처리·잔디·포인트를 갱신 (같은 트랜잭션 안)
        approachSubmissionService.applyFirstSolveRewards(submission, alreadySolved, userId, problem.getDifficulty(), category);

        // 6. 작업을 성공 처리 (제출 id 저장, 활성 상태 해제)
        task.markSucceeded(submission.getId(), now());

        // 7. 완료 알림을 만듦 (같은 트랜잭션)
        notificationService.createEvaluationNotification(
                userId, problemId, taskId, NotificationType.NL_EVALUATION_DONE, problem.getTitle());
    }

    /** 실패를 기록. 재시도할 수 있고 횟수가 남았으면 RETRY_WAIT, 아니면 FAILED. */
    @Transactional
    public void handleFailure(Long taskId, int attempt, EvaluationFailureCode code, String message, boolean retryable) {
        // 1. 작업을 잠그고, 이미 다른 경로가 처리했으면 아무것도 하지 않음
        SolutionEvaluationTask task = lockTask(taskId);
        if (!isCurrentAttempt(task, attempt)) {
            return;
        }

        // 2. 재시도 가능하고 횟수가 남았으면 일정 시간 뒤로 미루고, 아니면 최종 실패 처리
        LocalDateTime now = now();
        if (retryable && task.getWorkerAttemptCount() < properties.maxAttempts()) {
            task.scheduleRetry(now.plus(properties.retryDelay()), code.name(), message, now);
        } else {
            task.markFailed(code.name(), message, now);
            notifyFailed(task);
        }
    }

    /** 오래 PROCESSING으로 남아 있는 작업의 id 목록. */
    @Transactional(readOnly = true)
    public List<Long> findStuckTaskIds() {
        return taskRepository.findStuckIds(now().minus(properties.stuckAfter()));
    }

    /** 멈춘 작업 1건을 복구. 작업마다 별도 트랜잭션. */
    @Transactional
    public void recoverStuck(Long taskId) {
        // 1. 작업을 잠그고, 그 사이 처리되었거나 아직 기준 시간 안이면 건드리지 않음
        SolutionEvaluationTask task = lockTask(taskId);
        LocalDateTime now = now();
        if (task.getStatus() != EvaluationTaskStatus.PROCESSING
                || !task.getStartedAt().isBefore(now.minus(properties.stuckAfter()))) {
            return;
        }

        // 2. 횟수가 남았으면 바로 재시도할 수 있게 돌려놓고, 없으면 최종 실패 처리
        if (task.getWorkerAttemptCount() < properties.maxAttempts()) {
            task.scheduleRetry(now, EvaluationFailureCode.WORKER_TIMEOUT.name(), "worker_timeout", now);
        } else {
            task.markFailed(EvaluationFailureCode.WORKER_TIMEOUT.name(), "worker_timeout", now);
            notifyFailed(task);
        }
    }

    // 최종 실패 알림을 만듦 (문제 제목을 조회해 문구에 넣음)
    private void notifyFailed(SolutionEvaluationTask task) {
        Problem problem = findProblem(task.getProblemId());
        notificationService.createEvaluationNotification(
                task.getUserId(), task.getProblemId(), task.getId(),
                NotificationType.NL_EVALUATION_FAILED, problem.getTitle());
    }

    // 작업 조회 (없으면 404 예외를 던짐)
    private SolutionEvaluationTask findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "solution_evaluation_not_found"));
    }

    // 작업을 잠그고 조회 (없으면 404 예외를 던짐)
    private SolutionEvaluationTask lockTask(Long taskId) {
        return taskRepository.findForUpdateById(taskId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "solution_evaluation_not_found"));
    }

    // 문제 조회 (없으면 404 예외를 던짐)
    private Problem findProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));
    }

    // 지금 처리 중인 시도가 맞는지 확인
    private boolean isCurrentAttempt(SolutionEvaluationTask task, int attempt) {
        return task.getStatus() == EvaluationTaskStatus.PROCESSING && task.getWorkerAttemptCount() == attempt;
    }

    // 현재 시각 (UTC)
    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
