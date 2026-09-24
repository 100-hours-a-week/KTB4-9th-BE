package com.cosmos.cosmos_backend.approach.service;

import com.cosmos.cosmos_backend.ActivityRecord.domain.entity.ActivityRecord;
import com.cosmos.cosmos_backend.ActivityRecord.repository.ActivityRecordRepository;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationClient;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.ApproachSubmitResult;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.Keyword;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.domain.entity.RunningLimit;
import com.cosmos.cosmos_backend.problem.repository.KeywordRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.RunningLimitRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ApproachSubmissionService {

    private final ProblemRepository problemRepository;
    private final KeywordRepository keywordRepository;
    private final RunningLimitRepository runningLimitRepository;
    private final ApproachSubmissionRepository approachSubmissionRepository;
    private final AiEvaluationClient aiEvaluationClient;
    private final TransactionTemplate transactionTemplate;

    private final ActivityRecordRepository activityRecordRepository;

    public ApproachSubmitResult submit(Long userId, Long problemId, String selectedCategory, String naturalSolution) {
        // 1. problemId로 문제를 조회 (없으면 404 예외를 던짐)
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // 2. 선택한 카테고리를 enum으로 변환 (없는 값이면 400 예외를 던짐)
        Category category = parseCategory(selectedCategory);

        // 3. 선택한 카테고리와 문제의 정답 카테고리를 비교해 정답 여부를 계산 (오답이어도 계속 진행)
        boolean categoryResult = category.equals(problem.getCategory());

        // 4. AI 평가에 필요한 키워드·실행 제한을 조회하고 요청을 조립
        List<Keyword> keywords = keywordRepository.findByProblemIdOrderById(problemId);
        AiEvaluationRequest request = buildAiRequest(problem, naturalSolution, keywords, runningLimitRepository.findByProblemId(problemId));

        // 5. AI 서버에 평가 요청 (실패하면 예외가 던져져 여기서 끝나고 DB는 그대로 유지됨)
        AiEvaluationResult result = aiEvaluationClient.evaluate(request);

        // 6. AI 판정을 우리 키워드 목록 기준으로 병합
        List<AiEvaluationResult.KeywordJudgement> mergedKeywords = mergeKeywordJudgements(keywords, result.keywords());

        // 7. 성공했을 때만 이 트랜잭션 안에서 기존 제출을 갱신하거나 새로 저장
        ApproachSubmission submission = transactionTemplate.execute(status ->
                approachSubmissionRepository.findByUserIdAndProblemId(userId, problemId)
                        .map(existing -> {
                            existing.resubmit(category, naturalSolution, categoryResult, result.score(), result.feedback());
                            return existing;
                        })
                        .orElseGet(() -> approachSubmissionRepository.save(
                                new ApproachSubmission(userId, problemId, category, naturalSolution, categoryResult, result.score(), result.feedback())
                        ))
        );

        // 학습 기록
        // 1. 정답 + 자연어 풀이 100점이면 잔디 +1
        // 2. 기존에 해당 날짜에 대한 기록 있으면 그냥 + 1
        // 3. 기존에 해당 날짜에 대한 기록 없으면 행 새로 만들기
        if (submission.getCategoryResult() && submission.getTotalScore() == 100){

            LocalDate activityDate = LocalDate.now();
            Optional<ActivityRecord> activityRecord = activityRecordRepository.findByUserIdAndActivityDate(userId, activityDate);

            if (activityRecord.isPresent()) {
                ActivityRecord activity = activityRecord.get();
                activity.increaseCorrectProblemCount();
            } else {
                ActivityRecord activity = new ActivityRecord(userId, activityDate, 1L);
                activityRecordRepository.save(activity);
            }
        }

        return new ApproachSubmitResult(submission, mergedKeywords);
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

    // 문제·키워드·실행 제한을 AI 요청 형태로 조립
    private AiEvaluationRequest buildAiRequest(Problem problem, String naturalSolution, List<Keyword> keywords, List<RunningLimit> runningLimits) {
        // 1. 실행 제한을 AI 요청 형태로 변환 (MB → KB)
        List<AiProblemsCreateRequestDto.ExecutionLimits> executionLimits = runningLimits.stream()
                .map(limit -> new AiProblemsCreateRequestDto.ExecutionLimits(
                        limit.getLanguage(), limit.getTimeLimitMs(), limit.getMemoryLimitMb() * 1024))
                .toList();

        // 2. 요청 조립 (constraints는 Hibernate가 JSON에서 자동 변환해 둔 값을 그대로 씀)
        return new AiEvaluationRequest(
                problem.getTitle(),
                problem.getContent(),
                problem.getCategory().name(),
                problem.getCategorySelectReason(),
                keywords.stream().map(Keyword::getKeyword).toList(),
                problem.getConstraints(),
                executionLimits,
                naturalSolution
        );
    }

    // 우리 키워드 목록 순서를 기준으로 AI 판정을 병합 (AI가 언급 안 했으면 false, AI가 준 모르는 키워드는 버림)
    private List<AiEvaluationResult.KeywordJudgement> mergeKeywordJudgements(List<Keyword> ourKeywords, List<AiEvaluationResult.KeywordJudgement> aiKeywords) {
        // 1. AI 응답을 키워드 → 포함 여부 맵으로 변환
        Map<String, Boolean> aiJudgements = aiKeywords.stream()
                .collect(Collectors.toMap(AiEvaluationResult.KeywordJudgement::keyword, AiEvaluationResult.KeywordJudgement::included, (a, b) -> b));

        // 2. 우리 키워드 순서대로 판정을 채움
        return ourKeywords.stream()
                .map(keyword -> new AiEvaluationResult.KeywordJudgement(keyword.getKeyword(), aiJudgements.getOrDefault(keyword.getKeyword(), false)))
                .toList();
    }
}
