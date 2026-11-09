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
import com.cosmos.cosmos_backend.problem.repository.UsedHintRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UsedHintRepository usedHintRepository;
    private final Judge0Client judge0Client;
    private final TransactionTemplate transactionTemplate;

    /** submit()의 반환값: 저장된 제출 요약 + 테스트케이스별 판정 목록. */
    public record CodeSubmissionResult(CodeSubmission submission, List<JudgingResult> testResults) {}

    /** 코드를 채점하고 결과를 저장. 채점이 성공했을 때만 저장하고 제출 횟수를 올림. */
    public CodeSubmissionResult submit(Long userId, Long problemId, String language, String sourceCode) {
        // 1. 제출을 접수한 시각을 기록 (UTC 기준)
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC);

        // 2. problemId로 문제가 있는지 확인 (없으면 404 예외를 던짐)
        if (!problemRepository.existsById(problemId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found");
        }

        // 3. language 문자열을 enum으로 변환 (없는 값이면 400 예외를 던짐)
        Language lang = parseLanguage(language);

        // 4. 정답 힌트를 열람한 사용자는 제출할 수 없음 (409 예외를 던짐)
        boolean sawAnswerHint = usedHintRepository.findByUserIdAndProblemId(userId, problemId)
                .map(usedHint -> usedHint.getHintStage() >= 2)
                .orElse(false);
        if (sawAnswerHint) {
            throw new BusinessException(HttpStatus.CONFLICT, "answer_hint_already_used");
        }

        // 5. 이 문제의 제출 횟수가 한도를 채웠으면 Judge0를 부르기 전에 429 (잠금 없는 빠른 차단)
        int usedBefore = codeSubmissionRepository.findSubmittedCount(userId, problemId).orElse(0);
        if (usedBefore >= CodeSubmission.MAX_SUBMISSION_COUNT) {
            throw limitExceeded(usedBefore);
        }

        // 6. 채점에 쓸 테스트케이스와 이 언어의 실행 제한을 조회 (테스트케이스가 없으면 503 예외를 던짐)
        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByDisplayOrder(problemId);
        if (testCases.isEmpty()) {
            log.error("채점할 테스트케이스가 없음: problemId={}", problemId);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable");
        }
        RunningLimit limit = runningLimitRepository.findByProblemId(problemId).stream()
                .filter(runningLimit -> runningLimit.getLanguage() == lang)
                .findFirst()
                .orElse(null);

        // 7. Judge0에 채점 요청 (실패하면 예외가 던져져 여기서 끝나고 DB는 그대로 유지됨)
        List<JudgingResult> verdicts = judge0Client.judge(lang, sourceCode, testCases, limit);

        // 8. 통과 개수와 최종 결과를 계산 (앞 순서의 실패 종류가 결과, 모두 통과하면 CORRECT)
        int passed = (int) verdicts.stream().filter(verdict -> verdict == JudgingResult.CORRECT).count();
        JudgingResult result = verdicts.stream()
                .filter(verdict -> verdict != JudgingResult.CORRECT)
                .findFirst()
                .orElse(JudgingResult.CORRECT);

        // 9. 성공했을 때만 저장 (같은 사용자의 동시 최초 제출과 부딪히면 한 번 다시 시도)
        CodeSubmission submission = saveWithRetry(userId, problemId, lang, sourceCode, receivedAt, result, passed, verdicts.size());

        // 10. 테스트케이스별 판정도 같이 반환 (화면에서 테스트별 통과/실패 표시에 사용)
        return new CodeSubmissionResult(submission, verdicts);
    }

    // 저장을 시도하고, 동시 최초 제출과 유니크 제약이 충돌하면 한 번만 다시 시도
    private CodeSubmission saveWithRetry(Long userId, Long problemId, Language lang, String sourceCode,
                                         LocalDateTime receivedAt, JudgingResult result, int passed, int total) {
        try {
            // 1. 처음 시도
            return transactionTemplate.execute(status ->
                    saveSubmission(userId, problemId, lang, sourceCode, receivedAt, result, passed, total));
        } catch (DataIntegrityViolationException e) {
            // 2. 먼저 들어온 요청이 행을 만든 경우: 롤백됐으므로 새 트랜잭션에서 기존 행을 갱신
            log.warn("동시 최초 제출 충돌, 기존 행으로 다시 시도: userId={}, problemId={}", userId, problemId);
            return transactionTemplate.execute(status ->
                    saveSubmission(userId, problemId, lang, sourceCode, receivedAt, result, passed, total));
        }
    }

    // 신규 행은 잠금 없이 INSERT하고, 기존 행에만 비관적 락을 적용
    private CodeSubmission saveSubmission(Long userId, Long problemId, Language lang, String sourceCode,
                                          LocalDateTime receivedAt, JudgingResult result, int passed, int total) {
        // 없는 행을 FOR UPDATE로 조회하면 인덱스 gap lock이 생길 수 있으므로 먼저 일반 조회로 분기
        if (codeSubmissionRepository.findByUserIdAndProblemId(userId, problemId).isEmpty()) {
            return codeSubmissionRepository.save(
                    new CodeSubmission(userId, problemId, lang, sourceCode, receivedAt, result, passed, total)
            );
        }

        // 기존 행일 때만 잠근 뒤 제출 한도를 다시 확인하고 UPDATE
        CodeSubmission existing = codeSubmissionRepository
                .findForUpdateByUserIdAndProblemId(userId, problemId)
                .orElseThrow();
        if (existing.getSubmittedCount() >= CodeSubmission.MAX_SUBMISSION_COUNT) {
            throw limitExceeded(existing.getSubmittedCount());
        }
        existing.resubmit(lang, sourceCode, receivedAt, result, passed, total);
        return existing;
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
