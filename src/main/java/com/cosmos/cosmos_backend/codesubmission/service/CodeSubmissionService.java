package com.cosmos.cosmos_backend.codesubmission.service;

import com.cosmos.cosmos_backend.codesubmission.client.Judge0Client;
import com.cosmos.cosmos_backend.codesubmission.domain.CodeSubmission;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.codesubmission.dto.response.CodeSubmitResponse;
import com.cosmos.cosmos_backend.codesubmission.repository.CodeSubmissionRepository;
import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.RunningLimit;
import com.cosmos.cosmos_backend.problem.domain.entity.TestCase;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.RunningLimitRepository;
import com.cosmos.cosmos_backend.problem.repository.TestCaseRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSubmissionService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final RunningLimitRepository runningLimitRepository;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final Judge0Client judge0Client;
    private final TransactionTemplate transactionTemplate;

    /** 코드를 채점하고 결과를 저장. 채점이 성공했을 때만 저장하고 제출 횟수를 올림. */
    public CodeSubmission submit(Long userId, Long problemId, String language, String sourceCode) {
        // 1. 제출을 접수한 시각을 기록
        LocalDateTime receivedAt = LocalDateTime.now();

        // 2. problemId로 문제가 있는지 확인 (없으면 404 예외를 던짐)
        if (!problemRepository.existsById(problemId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found");
        }

        // 3. language 문자열을 enum으로 변환 (없는 값이면 400 예외를 던짐)
        Language lang = parseLanguage(language);

        // 4. 이 문제의 제출 횟수가 한도를 채웠으면 Judge0를 부르기 전에 429 (잠금 없는 빠른 차단)
        int usedBefore = codeSubmissionRepository.findSubmittedCount(userId, problemId).orElse(0);
        if (usedBefore >= CodeSubmission.MAX_SUBMISSION_COUNT) {
            throw limitExceeded(usedBefore);
        }

        // 5. 채점에 쓸 테스트케이스와 이 언어의 실행 제한을 조회 (테스트케이스가 없으면 503 예외를 던짐)
        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByDisplayOrder(problemId);
        if (testCases.isEmpty()) {
            log.error("채점할 테스트케이스가 없음: problemId={}", problemId);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable");
        }
        RunningLimit limit = runningLimitRepository.findByProblemId(problemId).stream()
                .filter(runningLimit -> runningLimit.getLanguage() == lang)
                .findFirst()
                .orElse(null);

        // 6. Judge0에 채점 요청 (실패하면 예외가 던져져 여기서 끝나고 DB는 그대로 유지됨)
        List<JudgingResult> verdicts = judge0Client.judge(lang, sourceCode, testCases, limit);

        // 7. 통과 개수와 최종 결과를 계산 (앞 순서의 실패 종류가 결과, 모두 통과하면 CORRECT)
        int passed = (int) verdicts.stream().filter(verdict -> verdict == JudgingResult.CORRECT).count();
        JudgingResult result = verdicts.stream()
                .filter(verdict -> verdict != JudgingResult.CORRECT)
                .findFirst()
                .orElse(JudgingResult.CORRECT);

        // 8. 성공했을 때만 이 트랜잭션 안에서 잠근 채로 기존 제출을 갱신하거나 새로 저장
        return transactionTemplate.execute(status ->
                codeSubmissionRepository.findForUpdateByUserIdAndProblemId(userId, problemId)
                        .map(existing -> {
                            // 1. 잠근 뒤 다시 확인 (앞의 빠른 확인 이후 다른 요청이 올렸을 수 있음)
                            if (existing.getSubmittedCount() >= CodeSubmission.MAX_SUBMISSION_COUNT) {
                                throw limitExceeded(existing.getSubmittedCount());
                            }
                            // 2. 재제출 처리 (커밋 때 UPDATE가 나감)
                            existing.resubmit(lang, sourceCode, receivedAt, result, passed, verdicts.size());
                            return existing;
                        })
                        .orElseGet(() -> codeSubmissionRepository.save(
                                new CodeSubmission(userId, problemId, lang, sourceCode, receivedAt, result, passed, verdicts.size())
                        ))
        );
    }

    // language 문자열을 Language enum으로 변환
    private Language parseLanguage(String value) {
        try {
            // 1. 문자열을 enum으로 변환
            return Language.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 2. 없는 값이면 400 예외를 던짐
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_language");
        }
    }

    // 제출 한도 초과 429 예외 생성 (data에 사용 횟수와 한도를 담음)
    private BusinessException limitExceeded(int usedCount) {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "submission_limit_exceeded",
                new CodeSubmitResponse.SubmissionLimitExceededData(usedCount, CodeSubmission.MAX_SUBMISSION_COUNT));
    }
}
