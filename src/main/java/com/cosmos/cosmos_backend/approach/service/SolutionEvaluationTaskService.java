package com.cosmos.cosmos_backend.approach.service;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.dto.response.EvaluationAcceptedResponse;
import com.cosmos.cosmos_backend.approach.dto.response.SolutionEvaluationResponse;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.service.ProblemService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 자연어 풀이 평가 작업을 접수하고 조회. AI 평가는 여기서 하지 않고 워커가 처리함. */
@Service
@RequiredArgsConstructor
public class SolutionEvaluationTaskService {

    // 최종 실패했을 때 사용자에게 보여줄 안내 문구
    private static final String FAILURE_MESSAGE = "평가를 완료하지 못했습니다. 다시 제출해 주세요.";

    private final SolutionEvaluationTaskRepository taskRepository;
    private final ProblemRepository problemRepository;
    private final ApproachSubmissionRepository approachSubmissionRepository;
    private final ProblemService problemService;
    private final NotificationService notificationService;
    private final Clock clock;

    /**
     * 풀이 평가를 접수. 작업만 저장하고 바로 반환함.
     * 저장 실패(동시 접수)를 잡아 다시 조회해야 하므로 메서드 전체를 트랜잭션으로 묶지 않음.
     */
    public EvaluationAcceptedResponse accept(Long userId, Long problemId, String selectedCategory, String naturalSolution) {
        // 1. 문제를 조회 (없으면 404 예외를 던짐)
        problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // 1-1. 열 수 없는 문제면 풀이를 접수하지 않음 (열 수 없는 문제에 풀이를 제출해서 문제를 읽는 우회를 막음)
        if (!problemService.canOpen(userId, problemId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found");
        }

        // 2. 선택한 카테고리를 enum으로 변환 (없는 값이면 400 예외를 던짐)
        Category category = parseCategory(selectedCategory);

        // 3. 성공한 제출 횟수가 한도를 채웠으면 429
        int usedCount = approachSubmissionRepository.findSubmittedCount(userId, problemId).orElse(0);
        if (usedCount >= ApproachSubmission.MAX_SUBMISSION_COUNT) {
            throw ApproachSubmissionService.limitExceeded(problemId, usedCount);
        }

        // 4. 이미 진행 중인 작업이 있으면 409
        taskRepository.findActive(userId, problemId).ifPresent(active -> {
            throw alreadyInProgress(active);
        });

        // 5. 작업을 저장 (같은 순간에 들어온 요청은 DB 유니크 제약이 막음)
        SolutionEvaluationTask task = new SolutionEvaluationTask(userId, problemId, category, naturalSolution, now());
        try {
            taskRepository.saveAndFlush(task);
        } catch (DataIntegrityViolationException e) {
            // 6. 동시에 접수된 요청이 먼저 저장된 경우: 그 작업을 찾아 409로 응답
            SolutionEvaluationTask active = taskRepository.findActive(userId, problemId).orElseThrow(() -> e);
            throw alreadyInProgress(active);
        }

        // 7. 접수한 작업을 반환 (사용 횟수는 접수로는 늘지 않음)
        return EvaluationAcceptedResponse.of(task, usedCount);
    }

    /** 평가 작업 조회. 본인 작업만 볼 수 있음. */
    @Transactional(readOnly = true)
    public SolutionEvaluationResponse get(Long userId, Long taskId) {
        // 1. 본인 작업만 조회 (없거나 남의 작업이면 404)
        SolutionEvaluationTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "solution_evaluation_not_found"));

        // 2. 같은 사용자·문제에서 가장 최근 작업을 찾아 이 작업이 최신인지 판단
        SolutionEvaluationTask latest = taskRepository
                .findFirstByUserIdAndProblemIdOrderByIdDesc(userId, task.getProblemId())
                .orElse(task);
        boolean isLatest = latest.getId().equals(task.getId());

        // 3. 성공한 제출 현황을 조회 (한 번도 성공하지 못했으면 0회)
        Optional<ApproachSubmission> submission = approachSubmissionRepository.findByUserIdAndProblemId(userId, task.getProblemId());
        int usedCount = submission.map(ApproachSubmission::getSubmittedCount).orElse(0);

        // 4. 최신 작업의 결과를 만듦: 성공이면 제출 행에서 평가 결과를, 실패면 실패 안내를
        ApproachSubmitResponse.Result result = null;
        SolutionEvaluationResponse.Failure failure = null;
        if (isLatest && task.getStatus() == EvaluationTaskStatus.SUCCEEDED) {
            // 제출 행은 계속 덮어쓰므로 이 작업이 만든 결과일 때만 보여줌
            result = submission.filter(s -> s.getId().equals(task.getSubmissionId()))
                    .flatMap(s -> problemRepository.findById(task.getProblemId())
                            .map(problem -> ApproachSubmitResponse.Result.from(s, problem)))
                    .orElse(null);
        } else if (isLatest && task.getStatus() == EvaluationTaskStatus.FAILED) {
            failure = new SolutionEvaluationResponse.Failure(task.getErrorCode(), FAILURE_MESSAGE, true);
        }

        // 5. 이 작업에 연결된 알림 id를 조회 (아직 알림이 없으면 null)
        Long notificationId = notificationService.findIdByEvaluationTaskId(taskId).orElse(null);

        // 6. 응답 조립
        return SolutionEvaluationResponse.of(task, latest, result, failure, usedCount, notificationId);
    }

    // 카테고리 문자열을 Category enum으로 변환
    private Category parseCategory(String value) {
        try {
            // 1. 문자열을 enum으로 변환
            return Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 2. 없는 값이면 400 예외를 던짐
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_selected_category");
        }
    }

    // 이미 진행 중인 작업이 있다는 409 예외 생성 (data에 그 작업의 id와 상태를 담음)
    private BusinessException alreadyInProgress(SolutionEvaluationTask active) {
        return new BusinessException(HttpStatus.CONFLICT, "solution_evaluation_already_in_progress",
                new EvaluationAcceptedResponse.InProgressData(active.getId(), active.getStatus().name()));
    }

    // 현재 시각 (UTC)
    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
