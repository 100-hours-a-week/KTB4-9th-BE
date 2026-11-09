package com.cosmos.cosmos_backend.approach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus;
import com.cosmos.cosmos_backend.approach.domain.KeywordResult;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.dto.response.EvaluationAcceptedResponse;
import com.cosmos.cosmos_backend.approach.dto.response.SolutionEvaluationResponse;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.service.ProblemService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SolutionEvaluationTaskServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 3, 30, 0);

    @Mock
    private SolutionEvaluationTaskRepository taskRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ApproachSubmissionRepository approachSubmissionRepository;

    @Mock
    private ProblemService problemService;

    @Mock
    private NotificationService notificationService;

    private SolutionEvaluationTaskService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-19T03:30:00Z"), ZoneId.of("Asia/Seoul"));
        service = new SolutionEvaluationTaskService(taskRepository, problemRepository, approachSubmissionRepository, problemService, notificationService, fixedClock);
    }

    private Problem problem(String category) {
        return new Problem(Difficulty.LV1, Category.valueOf(category), "제목", "내용", "입력 형식", "출력 형식", List.<AiProblemsCreateRequestDto.InputConstraints>of(), "선정 배경");
    }

    // id가 정해진 작업을 만든다.
    private SolutionEvaluationTask task(Long id, Long userId, Long problemId) {
        SolutionEvaluationTask task = new SolutionEvaluationTask(userId, problemId, Category.ARRAY, "이렇게 풉니다", CREATED_AT);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private SolutionEvaluationTask processing(Long id) {
        SolutionEvaluationTask task = task(id, 10L, 1L);
        task.startProcessing(CREATED_AT.plusSeconds(1));
        return task;
    }

    private ApproachSubmission submission(Long id, Category category, int score, int count) {
        ApproachSubmission submission = new ApproachSubmission(10L, 1L, category, "풀이", true, score, "좋은 접근이에요");
        submission.updateKeywordResults(List.of(new KeywordResult("점화식", true)));
        for (int i = 1; i < count; i++) {
            submission.resubmit(category, "풀이", true, score, "좋은 접근이에요");
        }
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }

    private void assertBusiness(Runnable action, HttpStatus status, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(status))
                .hasMessage(message);
    }

    // ===== accept =====

    @Test
    void accept_savesPendingTask_andReturnsUsageWithoutIncreasingCount() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(true);
        when(approachSubmissionRepository.findSubmittedCount(10L, 1L)).thenReturn(Optional.of(2));
        when(taskRepository.findActive(10L, 1L)).thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SolutionEvaluationTask saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9001L);
            return saved;
        });

        // When
        EvaluationAcceptedResponse response = service.accept(10L, 1L, "BINARY_SEARCH", "중간값을 확인합니다");

        // Then
        ArgumentCaptor<SolutionEvaluationTask> captor = ArgumentCaptor.forClass(SolutionEvaluationTask.class);
        verify(taskRepository).saveAndFlush(captor.capture());
        SolutionEvaluationTask saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getProblemId()).isEqualTo(1L);
        assertThat(saved.getSelectedCategory()).isEqualTo(Category.BINARY_SEARCH);
        assertThat(saved.getNaturalSolution()).isEqualTo("중간값을 확인합니다");
        assertThat(saved.getStatus()).isEqualTo(EvaluationTaskStatus.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(response.evaluationTaskId()).isEqualTo(9001L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-10-19T03:30:00Z"));
        assertThat(response.submissionUsage()).isEqualTo(new ApproachSubmitResponse.SubmissionUsage(5, 2, 3));
    }

    @Test
    void accept_acceptsFifthSubmission_whenFourSucceededBefore() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(true);
        when(approachSubmissionRepository.findSubmittedCount(10L, 1L)).thenReturn(Optional.of(4));
        when(taskRepository.findActive(10L, 1L)).thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        EvaluationAcceptedResponse response = service.accept(10L, 1L, "ARRAY", "풀이");

        // Then
        assertThat(response.submissionUsage().remainingCount()).isEqualTo(1);
    }

    @Test
    void accept_throwsNotFound_andSavesNothing_whenProblemMissing() {
        // Given
        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertBusiness(() -> service.accept(10L, 999L, "ARRAY", "풀이"), HttpStatus.NOT_FOUND, "problem_not_found");
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_throwsNotFound_andSavesNothing_whenProblemCannotBeOpenedByUser() {
        // Given: 문제는 있지만 이 사용자가 열 수 없는 문제
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(false);

        // When & Then
        assertBusiness(() -> service.accept(10L, 1L, "ARRAY", "풀이"), HttpStatus.NOT_FOUND, "problem_not_found");
        verify(approachSubmissionRepository, never()).findSubmittedCount(any(), any());
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_throwsBadRequest_andSavesNothing_whenSelectedCategoryInvalid() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(true);

        // When & Then
        assertBusiness(() -> service.accept(10L, 1L, "NOT_A_CATEGORY", "풀이"), HttpStatus.BAD_REQUEST, "invalid_selected_category");
        verify(approachSubmissionRepository, never()).findSubmittedCount(any(), any());
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_throws429WithNestedUsage_andSavesNothing_whenLimitAlreadyReached() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(true);
        when(approachSubmissionRepository.findSubmittedCount(10L, 1L)).thenReturn(Optional.of(5));

        // When & Then
        assertThatThrownBy(() -> service.accept(10L, 1L, "ARRAY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException exception = (BusinessException) e;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getData()).isEqualTo(new ApproachSubmitResponse.SubmissionLimitExceededData(
                            1L, new ApproachSubmitResponse.SubmissionUsage(5, 5, 0)));
                })
                .hasMessage("solution_submission_limit_exceeded");
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_throws409WithActiveTask_andSavesNothing_whenEvaluationAlreadyInProgress() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(true);
        when(approachSubmissionRepository.findSubmittedCount(10L, 1L)).thenReturn(Optional.empty());
        when(taskRepository.findActive(10L, 1L)).thenReturn(Optional.of(processing(9001L)));

        // When & Then
        assertThatThrownBy(() -> service.accept(10L, 1L, "ARRAY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException exception = (BusinessException) e;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getData()).isEqualTo(new EvaluationAcceptedResponse.InProgressData(9001L, "PROCESSING"));
                })
                .hasMessage("solution_evaluation_already_in_progress");
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_throws409_whenAnotherRequestSavedFirst_andDatabaseRejectsDuplicate() {
        // Given: 확인 시점엔 없었는데 저장하는 순간 다른 요청이 먼저 저장해서 DB 제약에 걸림
        SolutionEvaluationTask winner = task(9001L, 10L, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(true);
        when(approachSubmissionRepository.findSubmittedCount(10L, 1L)).thenReturn(Optional.empty());
        when(taskRepository.findActive(10L, 1L)).thenReturn(Optional.empty(), Optional.of(winner));
        when(taskRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        // When & Then
        assertThatThrownBy(() -> service.accept(10L, 1L, "ARRAY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException exception = (BusinessException) e;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getData()).isEqualTo(new EvaluationAcceptedResponse.InProgressData(9001L, "PENDING"));
                });
    }

    @Test
    void accept_rethrowsOriginalError_whenConstraintFailsButNoActiveTaskExists() {
        // Given: 활성 작업 때문이 아닌 다른 제약 오류
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(problemService.canOpen(10L, 1L)).thenReturn(true);
        when(approachSubmissionRepository.findSubmittedCount(10L, 1L)).thenReturn(Optional.empty());
        when(taskRepository.findActive(10L, 1L)).thenReturn(Optional.empty(), Optional.empty());
        when(taskRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("other constraint"));

        // When & Then
        assertThatThrownBy(() -> service.accept(10L, 1L, "ARRAY", "풀이"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ===== get =====

    @Test
    void get_throwsNotFound_whenTaskMissingOrNotOwnedByUser() {
        // Given: 다른 사용자의 작업은 사용자 id로 찾으면 나오지 않음
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.empty());

        // When & Then
        assertBusiness(() -> service.get(10L, 9001L), HttpStatus.NOT_FOUND, "solution_evaluation_not_found");
    }

    @Test
    void get_returnsPendingWithoutResult_andZeroUsage_whenNeverSucceeded() {
        // Given
        SolutionEvaluationTask pending = task(9001L, 10L, 1L);
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(pending));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(pending));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());

        // When
        SolutionEvaluationResponse response = service.get(10L, 9001L);

        // Then
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.isLatest()).isTrue();
        assertThat(response.latestEvaluation()).isEqualTo(new SolutionEvaluationResponse.LatestEvaluation(9001L, "PENDING"));
        assertThat(response.submissionId()).isNull();
        assertThat(response.result()).isNull();
        assertThat(response.failure()).isNull();
        assertThat(response.notificationId()).isNull();
        assertThat(response.submissionUsage()).isEqualTo(new ApproachSubmitResponse.SubmissionUsage(5, 0, 5));
        assertThat(response.selectedCategory()).isEqualTo("ARRAY");
        assertThat(response.naturalSolution()).isEqualTo("이렇게 풉니다");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-10-19T03:30:00Z"));
        assertThat(response.startedAt()).isNull();
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void get_returnsRetryWaitWithNextRetryAt() {
        // Given
        SolutionEvaluationTask retry = processing(9001L);
        retry.scheduleRetry(CREATED_AT.plusSeconds(16), "AI_SERVER_TIMEOUT", "evaluation_timeout", CREATED_AT.plusSeconds(1));
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(retry));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(retry));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());

        // When
        SolutionEvaluationResponse response = service.get(10L, 9001L);

        // Then
        assertThat(response.status()).isEqualTo("RETRY_WAIT");
        assertThat(response.nextRetryAt()).isEqualTo(Instant.parse("2026-10-19T03:30:16Z"));
        assertThat(response.startedAt()).isEqualTo(Instant.parse("2026-10-19T03:30:01Z"));
        assertThat(response.failure()).isNull();
    }

    @Test
    void get_returnsResultFromSubmission_whenLatestTaskSucceeded() {
        // Given: 카테고리를 맞힌 제출이라 정답 카테고리도 공개됨
        SolutionEvaluationTask succeeded = processing(9001L);
        succeeded.markSucceeded(501L, CREATED_AT.plusSeconds(6));
        ApproachSubmission submission = submission(501L, Category.ARRAY, 90, 3);
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(succeeded));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(succeeded));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(submission));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));

        // When
        SolutionEvaluationResponse response = service.get(10L, 9001L);

        // Then
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.isLatest()).isTrue();
        assertThat(response.submissionId()).isEqualTo(501L);
        assertThat(response.result().isCorrect()).isTrue();
        assertThat(response.result().approachScore()).isEqualTo(90);
        assertThat(response.result().aiFeedback()).isEqualTo("좋은 접근이에요");
        assertThat(response.result().keywords()).containsExactly(new ApproachSubmitResponse.KeywordJudgement("점화식", true));
        assertThat(response.result().correctCategory()).isEqualTo("ARRAY");
        assertThat(response.submissionUsage()).isEqualTo(new ApproachSubmitResponse.SubmissionUsage(5, 3, 2));
        assertThat(response.completedAt()).isEqualTo(Instant.parse("2026-10-19T03:30:06Z"));
        assertThat(response.failure()).isNull();
    }

    @Test
    void get_returnsNotificationId_whenNotificationWasCreatedForTask() {
        // Given
        SolutionEvaluationTask succeeded = processing(9001L);
        succeeded.markSucceeded(501L, CREATED_AT.plusSeconds(6));
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(succeeded));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(succeeded));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(notificationService.findIdByEvaluationTaskId(9001L)).thenReturn(Optional.of(7001L));

        // When
        SolutionEvaluationResponse response = service.get(10L, 9001L);

        // Then
        assertThat(response.notificationId()).isEqualTo(7001L);
    }

    @Test
    void get_hidesCorrectCategory_whenAnswerWasWrongAndLimitRemains() {
        // Given
        SolutionEvaluationTask succeeded = processing(9001L);
        succeeded.markSucceeded(501L, CREATED_AT.plusSeconds(6));
        ApproachSubmission wrong = new ApproachSubmission(10L, 1L, Category.DP, "풀이", false, 40, "아쉬워요");
        ReflectionTestUtils.setField(wrong, "id", 501L);
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(succeeded));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(succeeded));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(wrong));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));

        // When
        SolutionEvaluationResponse response = service.get(10L, 9001L);

        // Then
        assertThat(response.result().isCorrect()).isFalse();
        assertThat(response.result().correctCategory()).isNull();
        assertThat(response.result().categorySelectReason()).isNull();
    }

    @Test
    void get_returnsFailureWithCodeAndResubmitHint_whenLatestTaskFailed() {
        // Given
        SolutionEvaluationTask failed = processing(9001L);
        failed.markFailed("AI_SERVER_UNAVAILABLE", "ai_server_unavailable", CREATED_AT.plusSeconds(40));
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(failed));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(failed));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());

        // When
        SolutionEvaluationResponse response = service.get(10L, 9001L);

        // Then
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failure()).isEqualTo(new SolutionEvaluationResponse.Failure(
                "AI_SERVER_UNAVAILABLE", "평가를 완료하지 못했습니다. 다시 제출해 주세요.", true));
        assertThat(response.result()).isNull();
        assertThat(response.submissionId()).isNull();
    }

    @Test
    void get_hidesResultAndFailure_whenTaskIsNotTheLatestOne() {
        // Given: 9001은 성공했지만 이후 9004가 접수되어 최신이 아님
        SolutionEvaluationTask old = processing(9001L);
        old.markSucceeded(501L, CREATED_AT.plusSeconds(6));
        SolutionEvaluationTask latest = task(9004L, 10L, 1L);
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(old));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(latest));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(submission(501L, Category.ARRAY, 90, 3)));
        when(notificationService.findIdByEvaluationTaskId(9001L)).thenReturn(Optional.of(7001L));

        // When
        SolutionEvaluationResponse response = service.get(10L, 9001L);

        // Then: 과거 작업도 그 작업의 알림 id는 반환
        assertThat(response.notificationId()).isEqualTo(7001L);
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.isLatest()).isFalse();
        assertThat(response.latestEvaluation()).isEqualTo(new SolutionEvaluationResponse.LatestEvaluation(9004L, "PENDING"));
        assertThat(response.submissionId()).isNull();
        assertThat(response.result()).isNull();
        assertThat(response.failure()).isNull();
        assertThat(response.submissionUsage().usedCount()).isEqualTo(3);
        verify(problemRepository, never()).findById(any());
    }

    @Test
    void get_hidesResult_whenSubmissionWasMadeByAnotherTask() {
        // Given: 이 작업의 제출 id와 지금 제출 행의 id가 다름
        SolutionEvaluationTask succeeded = processing(9001L);
        succeeded.markSucceeded(500L, CREATED_AT.plusSeconds(6));
        when(taskRepository.findByIdAndUserId(9001L, 10L)).thenReturn(Optional.of(succeeded));
        when(taskRepository.findFirstByUserIdAndProblemIdOrderByIdDesc(10L, 1L)).thenReturn(Optional.of(succeeded));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(submission(501L, Category.ARRAY, 90, 3)));

        // When & Then
        assertThat(service.get(10L, 9001L).result()).isNull();
    }
}
