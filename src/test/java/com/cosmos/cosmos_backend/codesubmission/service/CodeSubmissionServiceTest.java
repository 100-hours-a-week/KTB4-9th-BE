package com.cosmos.cosmos_backend.codesubmission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.codesubmission.client.Judge0Client;
import com.cosmos.cosmos_backend.codesubmission.domain.CodeSubmission;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingStatus;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CodeSubmissionServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long PROBLEM_ID = 1L;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private RunningLimitRepository runningLimitRepository;

    @Mock
    private CodeSubmissionRepository codeSubmissionRepository;

    @Mock
    private Judge0Client judge0Client;

    private CodeSubmissionService service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate 없이도 콜백이 바로 실행되도록 최소한으로 흉내냄 (진짜 트랜잭션은 안 씀)
        TransactionTemplate fakeTransactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        service = new CodeSubmissionService(
                problemRepository, testCaseRepository, runningLimitRepository,
                codeSubmissionRepository, judge0Client, fakeTransactionTemplate
        );
    }

    private List<TestCase> testCases(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new TestCase(PROBLEM_ID, "입력" + i, "출력" + i, i))
                .toList();
    }

    // 문제가 있고 테스트케이스가 n개 있는 상태를 만든다. (만든 테스트케이스 목록을 반환)
    private List<TestCase> givenTestCases(int count) {
        List<TestCase> testCases = testCases(count);
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(testCaseRepository.findByProblemIdOrderByDisplayOrder(PROBLEM_ID)).thenReturn(testCases);
        return testCases;
    }

    // 이 문제에 언어별 실행 제한이 있는 상태를 만든다.
    private void givenLimits(RunningLimit... limits) {
        when(runningLimitRepository.findByProblemId(PROBLEM_ID)).thenReturn(List.of(limits));
    }

    // 이 사용자의 이전 제출이 없는 상태(첫 제출)를 만든다.
    private void givenNoPreviousSubmission() {
        when(codeSubmissionRepository.findSubmittedCount(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());
        when(codeSubmissionRepository.findForUpdateByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());
        when(codeSubmissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 이 사용자가 이미 count번 제출한 상태를 만든다. (잠근 뒤에도 같은 횟수)
    private CodeSubmission givenPreviousSubmissions(int count) {
        CodeSubmission existing = existingWithCount(count);
        when(codeSubmissionRepository.findSubmittedCount(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(count));
        when(codeSubmissionRepository.findForUpdateByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(existing));
        return existing;
    }

    private void assertBusiness(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(status))
                .hasMessage(message);
    }

    @Test
    void submit_savesCorrect_whenAllTestCasesPass() {
        // Given
        givenTestCases(3);
        givenLimits();
        givenNoPreviousSubmission();
        when(judge0Client.judge(any(), any(), any(), any()))
                .thenReturn(List.of(JudgingResult.CORRECT, JudgingResult.CORRECT, JudgingResult.CORRECT));

        // When
        CodeSubmission submission = service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)");

        // Then
        assertThat(submission.getJudgingResult()).isEqualTo(JudgingResult.CORRECT);
        assertThat(submission.getPassedTestCount()).isEqualTo(3);
        assertThat(submission.getTotalTestCount()).isEqualTo(3);
        assertThat(submission.getJudgingStatus()).isEqualTo(JudgingStatus.COMPLETED);
        assertThat(submission.getSubmittedCount()).isEqualTo(1);
        assertThat(submission.getLanguage()).isEqualTo(Language.PYTHON);
        assertThat(submission.getSourceCode()).isEqualTo("print(1)");
    }

    @Test
    void submit_usesFirstFailureAsResult_andCountsPassed() {
        // Given: 통과, 오답, 시간 초과, 통과 → 가장 앞 실패인 오답이 결과
        givenTestCases(4);
        givenLimits();
        givenNoPreviousSubmission();
        when(judge0Client.judge(any(), any(), any(), any())).thenReturn(List.of(
                JudgingResult.CORRECT, JudgingResult.WRONG_ANSWER, JudgingResult.TIME_LIMIT_EXCEEDED, JudgingResult.CORRECT
        ));

        // When
        CodeSubmission submission = service.submit(USER_ID, PROBLEM_ID, "JAVA", "class Main {}");

        // Then
        assertThat(submission.getJudgingResult()).isEqualTo(JudgingResult.WRONG_ANSWER);
        assertThat(submission.getPassedTestCount()).isEqualTo(2);
        assertThat(submission.getTotalTestCount()).isEqualTo(4);
    }

    @Test
    void submit_passesLanguageSourceTestCasesAndLimitOfThatLanguageToJudge0() {
        // Given
        List<TestCase> testCases = givenTestCases(2);
        RunningLimit pythonLimit = new RunningLimit(PROBLEM_ID, Language.PYTHON, 3000F, 256);
        RunningLimit javaLimit = new RunningLimit(PROBLEM_ID, Language.JAVA, 2000F, 256);
        givenLimits(javaLimit, pythonLimit);
        givenNoPreviousSubmission();
        when(judge0Client.judge(any(), any(), any(), any())).thenReturn(List.of(JudgingResult.CORRECT, JudgingResult.CORRECT));

        // When
        service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)");

        // Then
        verify(judge0Client).judge(Language.PYTHON, "print(1)", testCases, pythonLimit);
    }

    @Test
    void submit_passesNullLimit_whenLanguageHasNoRunningLimit() {
        // Given
        givenTestCases(1);
        givenLimits();
        givenNoPreviousSubmission();
        when(judge0Client.judge(any(), any(), any(), any())).thenReturn(List.of(JudgingResult.CORRECT));

        // When
        service.submit(USER_ID, PROBLEM_ID, "CPP", "int main() {}");

        // Then
        verify(judge0Client).judge(any(), any(), any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void submit_keepsReceivedTimeBeforeJudgedTime() {
        // Given
        givenTestCases(1);
        givenLimits();
        givenNoPreviousSubmission();
        LocalDateTime before = LocalDateTime.now();
        when(judge0Client.judge(any(), any(), any(), any())).thenReturn(List.of(JudgingResult.CORRECT));

        // When
        CodeSubmission submission = service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)");

        // Then
        assertThat(submission.getSubmittedAt()).isAfterOrEqualTo(before);
        assertThat(submission.getJudgedAt()).isAfterOrEqualTo(submission.getSubmittedAt());
    }

    @Test
    void submit_resubmitsExisting_increasingCountAndOverwritingResult() {
        // Given: 이미 2번 제출한 기록이 있음
        givenTestCases(2);
        givenLimits();
        CodeSubmission existing = givenPreviousSubmissions(2);
        when(judge0Client.judge(any(), any(), any(), any())).thenReturn(List.of(JudgingResult.CORRECT, JudgingResult.CORRECT));

        // When
        CodeSubmission submission = service.submit(USER_ID, PROBLEM_ID, "JAVASCRIPT", "console.log(1)");

        // Then
        assertThat(submission).isSameAs(existing);
        assertThat(submission.getSubmittedCount()).isEqualTo(3);
        assertThat(submission.getJudgingResult()).isEqualTo(JudgingResult.CORRECT);
        assertThat(submission.getLanguage()).isEqualTo(Language.JAVASCRIPT);
        assertThat(submission.getSourceCode()).isEqualTo("console.log(1)");
        verify(codeSubmissionRepository, never()).save(any());
    }

    @Test
    void submit_allowsFifthSubmission() {
        // Given: 이미 4번 제출
        givenTestCases(1);
        givenLimits();
        givenPreviousSubmissions(4);
        when(judge0Client.judge(any(), any(), any(), any())).thenReturn(List.of(JudgingResult.CORRECT));

        // When
        CodeSubmission submission = service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)");

        // Then
        assertThat(submission.getSubmittedCount()).isEqualTo(5);
    }

    @Test
    void submit_throws429WithUsageData_andNeverCallsJudge0_whenLimitAlreadyReached() {
        // Given: 이미 5번 제출
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(codeSubmissionRepository.findSubmittedCount(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(5));

        // When & Then
        assertThatThrownBy(() -> service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(be.getData()).isEqualTo(new CodeSubmitResponse.SubmissionLimitExceededData(5, 5));
                })
                .hasMessage("submission_limit_exceeded");
        verify(judge0Client, never()).judge(any(), any(), any(), any());
    }

    @Test
    void submit_throws429AndKeepsPreviousResult_whenLimitReachedByAnotherRequestAfterLock() {
        // Given: 빠른 확인 때는 4번이었는데, 잠근 뒤에는 5번 (다른 요청이 먼저 올림)
        givenTestCases(1);
        givenLimits();
        CodeSubmission existing = existingWithCount(5);
        when(codeSubmissionRepository.findSubmittedCount(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(4));
        when(codeSubmissionRepository.findForUpdateByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(existing));
        when(judge0Client.judge(any(), any(), any(), any())).thenReturn(List.of(JudgingResult.CORRECT));

        // When & Then
        assertBusiness(() -> service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)"),
                HttpStatus.TOO_MANY_REQUESTS, "submission_limit_exceeded");
        assertThat(existing.getSubmittedCount()).isEqualTo(5);
        assertThat(existing.getSourceCode()).isNotEqualTo("print(1)");
    }

    @Test
    void submit_savesNothingAndKeepsCount_whenJudge0Fails() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(codeSubmissionRepository.findSubmittedCount(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(2));
        when(testCaseRepository.findByProblemIdOrderByDisplayOrder(PROBLEM_ID)).thenReturn(testCases(2));
        when(runningLimitRepository.findByProblemId(PROBLEM_ID)).thenReturn(List.of());
        when(judge0Client.judge(any(), any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable"));

        // When & Then
        assertBusiness(() -> service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)"),
                HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable");
        verify(codeSubmissionRepository, never()).save(any());
        verify(codeSubmissionRepository, never()).findForUpdateByUserIdAndProblemId(any(), any());
    }

    @Test
    void submit_throws503_whenNoTestCases() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(codeSubmissionRepository.findSubmittedCount(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());
        when(testCaseRepository.findByProblemIdOrderByDisplayOrder(PROBLEM_ID)).thenReturn(List.of());

        // When & Then
        assertBusiness(() -> service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)"),
                HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable");
        verify(judge0Client, never()).judge(any(), any(), any(), any());
    }

    @Test
    void submit_throws404_whenProblemMissing() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(false);

        // When & Then
        assertBusiness(() -> service.submit(USER_ID, PROBLEM_ID, "PYTHON", "print(1)"),
                HttpStatus.NOT_FOUND, "problem_not_found");
    }

    @Test
    void submit_throws400_whenLanguageInvalid() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);

        // When & Then
        assertBusiness(() -> service.submit(USER_ID, PROBLEM_ID, "RUBY", "puts 1"),
                HttpStatus.BAD_REQUEST, "invalid_language");
        verify(judge0Client, never()).judge(any(), any(), any(), any());
    }

    // 이미 count번 제출한 기존 제출을 만든다.
    private CodeSubmission existingWithCount(int count) {
        CodeSubmission existing = new CodeSubmission(
                USER_ID, PROBLEM_ID, Language.PYTHON, "old", LocalDateTime.now().minusMinutes(10), JudgingResult.WRONG_ANSWER, 0, 1
        );
        for (int i = 1; i < count; i++) {
            existing.resubmit(Language.PYTHON, "old", LocalDateTime.now().minusMinutes(10 - i), JudgingResult.WRONG_ANSWER, 0, 1);
        }
        return existing;
    }
}
