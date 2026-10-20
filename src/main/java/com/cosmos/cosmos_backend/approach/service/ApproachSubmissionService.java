package com.cosmos.cosmos_backend.approach.service;

import com.cosmos.cosmos_backend.activityRecord.domain.entity.ActivityRecord;
import com.cosmos.cosmos_backend.activityRecord.repository.ActivityRecordRepository;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.dto.response.CategoryAnswerResponse;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.Keyword;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.domain.entity.RunningLimit;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.cosmos.cosmos_backend.ranking.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApproachSubmissionService {

    private final ProblemRepository problemRepository;
    private final ApproachSubmissionRepository approachSubmissionRepository;

    private final ActivityRecordRepository activityRecordRepository;


    private final UserPointService userPointService;

    private final Clock clock;

    // 제출한 적 있는 사용자에게 문제의 정답 카테고리와 선정 배경을 알려줌
    public CategoryAnswerResponse getCategoryAnswer(Long userId, Long problemId) {
        // 1. 이 사용자의 제출 기록이 없으면 403 (풀지 않고 정답만 받는 것을 막음)
        approachSubmissionRepository.findByUserIdAndProblemId(userId, problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "category_answer_not_available"));

        // 2. 문제를 조회 (없으면 404 예외를 던짐)
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // 3. 정답 카테고리와 선정 배경을 반환
        return new CategoryAnswerResponse(problemId, problem.getCategory().name(), problem.getCategorySelectReason());
    }

    // 해당 문제를 처음 맞힌 경우에만 풀이 처리, 잔디, 포인트를 갱신 (오답이거나 이미 맞힌 문제면 아무것도 안 함)
    void applyFirstSolveRewards(ApproachSubmission submission, boolean alreadySolved, Long userId, Difficulty difficulty, Category category) {
        // 이번 제출이 정답인지 확인
        Boolean correct = submission.getCategoryResult() && submission.getTotalScore() >= 85;


        // 해당 문제에 대해 최초 정답일 경우에만 잔디, 포인트 갱신
        // 학습 기록
        // 0. 해당 문제에 대해 첫 정답인지 확인, 첫 정답일 경우에만 잔디, 포인트 업데이트
        // 1. 정답 + 자연어 풀이 100점이면 잔디 +1
        // 2. 기존에 해당 날짜에 대한 기록 있으면 그냥 + 1
        // 3. 기존에 해당 날짜에 대한 기록 없으면 행 새로 만들기
        // 포인트 갱신
        // 0. 전체, 난이도, 카테고리별로 각각 업데이트
        if (!alreadySolved && correct) {

            // solved = true
            submission.markAsSolved();

            // 오늘의 학습 기록 갱신
            LocalDate activityDate = LocalDate.now(clock);

            Optional<ActivityRecord> activityRecord =
                    activityRecordRepository.findByUserIdAndActivityDate(
                            userId,
                            activityDate
                    );

            if (activityRecord.isPresent()) {

                ActivityRecord activity = activityRecord.get();
                activity.increaseCorrectProblemCount();

            } else {

                ActivityRecord activity =
                        new ActivityRecord(
                                userId,
                                activityDate,
                                1L
                        );

                activityRecordRepository.save(activity);
            }

            // 포인트 / 랭킹 갱신
            userPointService.updatePoint(
                    userId,
                    difficulty,
                    category
            );
        }
    }

    // 제출 한도 초과 429 예외 생성 (data에 문제 번호와 사용 현황을 담음)
    static BusinessException limitExceeded(Long problemId, int usedCount) {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "solution_submission_limit_exceeded",
                new ApproachSubmitResponse.SubmissionLimitExceededData(problemId, ApproachSubmitResponse.SubmissionUsage.of(usedCount)));
    }

    // 문제·키워드·실행 제한을 AI 요청 형태로 조립
    static AiEvaluationRequest buildAiRequest(Problem problem, String naturalSolution, List<Keyword> keywords, List<RunningLimit> runningLimits) {
        // 1. 실행 제한을 AI 요청 형태로 변환 (enum은 이름 그대로 보냄)
        List<AiEvaluationRequest.ExecutionLimit> executionLimits = runningLimits.stream()
                .map(limit -> new AiEvaluationRequest.ExecutionLimit(
                        limit.getLanguage().name(), limit.getTimeLimitMs(), limit.getMemoryLimitKb()))
                .toList();

        // 2. 입력 제한을 AI 요청 형태로 변환 (scope·dataType 모두 우리 enum 이름 그대로 보냄)
        List<AiEvaluationRequest.InputConstraint> inputConstraints = problem.getConstraints().stream()
                .map(constraint -> new AiEvaluationRequest.InputConstraint(
                        constraint.target(),
                        constraint.scope().name(),
                        constraint.dataType().name(),
                        constraint.minValue(),
                        constraint.maxValue(),
                        constraint.specialConditions()
                ))
                .toList();

        // 3. 요청 조립
        return new AiEvaluationRequest(
                problem.getTitle(),
                problem.getContent(),
                problem.getCategory().name(),
                problem.getCategorySelectReason(),
                keywords.stream().map(Keyword::getKeyword).toList(),
                inputConstraints,
                executionLimits,
                naturalSolution
        );
    }

    // 우리 키워드 목록 순서를 기준으로 AI 판정을 병합 (AI가 언급 안 했으면 false, AI가 준 모르는 키워드는 버림)
    static List<AiEvaluationResult.KeywordJudgement> mergeKeywordJudgements(List<Keyword> ourKeywords, List<AiEvaluationResult.KeywordJudgement> aiKeywords) {
        // 1. AI 응답을 키워드 → 포함 여부 맵으로 변환
        Map<String, Boolean> aiJudgements = aiKeywords.stream()
                .collect(Collectors.toMap(AiEvaluationResult.KeywordJudgement::keyword, AiEvaluationResult.KeywordJudgement::included, (a, b) -> b));

        // 2. 우리 키워드 순서대로 판정을 채움
        return ourKeywords.stream()
                .map(keyword -> new AiEvaluationResult.KeywordJudgement(keyword.getKeyword(), aiJudgements.getOrDefault(keyword.getKeyword(), false)))
                .toList();
    }
}
