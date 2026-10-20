package com.cosmos.cosmos_backend.approach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus;
import com.cosmos.cosmos_backend.approach.domain.KeywordResult;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService.EvaluationInput;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.Keyword;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.repository.KeywordRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.RunningLimitRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SolutionEvaluationExecutionServiceTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 19, 3, 30, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 30, 6);

    @Mock
    private SolutionEvaluationTaskRepository taskRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private RunningLimitRepository runningLimitRepository;

    @Mock
    private ApproachSubmissionRepository approachSubmissionRepository;

    @Mock
    private ApproachSubmissionService approachSubmissionService;

    private SolutionEvaluationExecutionService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-19T03:30:06Z"), ZoneId.of("Asia/Seoul"));
        service = new SolutionEvaluationExecutionService(
                taskRepository, problemRepository, keywordRepository, runningLimitRepository,
                approachSubmissionRepository, approachSubmissionService, fixedClock
        );
    }

    private Problem problem(String category) {
        return new Problem(Difficulty.LV1, Category.valueOf(category), "제목", "내용", "입력 형식", "출력 형식", List.<AiProblemsCreateRequestDto.InputConstraints>of(), "선정 배경");
    }

    private SolutionEvaluationTask pendingTask(Category selectedCategory) {
        return new SolutionEvaluationTask(10L, 1L, selectedCategory, "이렇게 풉니다", REQUESTED_AT);
    }

    private SolutionEvaluationTask processingTask(Category selectedCategory) {
        SolutionEvaluationTask task = pendingTask(selectedCategory);
        task.startProcessing(REQUESTED_AT.plusSeconds(1));
        return task;
    }

    private AiEvaluationResult aiResult(String... includedKeywords) {
        List<AiEvaluationResult.KeywordJudgement> judgements = List.of(includedKeywords).stream()
                .map(keyword -> new AiEvaluationResult.KeywordJudgement(keyword, true))
                .toList();
        return new AiEvaluationResult(85, "좋은 접근이에요", judgements);
    }

    @Test
    void claimNext_marksTaskProcessing_andReturnsItsId() {
        // Given
        SolutionEvaluationTask task = pendingTask(Category.ARRAY);
        ReflectionTestUtils.setField(task, "id", 5L);
        when(taskRepository.findNextForUpdate()).thenReturn(Optional.of(task));

        // When
        Optional<Long> claimed = service.claimNext();

        // Then
        assertThat(claimed).contains(5L);
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PROCESSING);
        assertThat(task.getWorkerAttemptCount()).isEqualTo(1);
        assertThat(task.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void claimNext_returnsEmpty_whenNoPendingTask() {
        // Given
        when(taskRepository.findNextForUpdate()).thenReturn(Optional.empty());

        // When & Then
        assertThat(service.claimNext()).isEmpty();
    }

    @Test
    void prepare_buildsAiRequestWithAnswerCategory_andReturnsKeywords() {
        // Given
        SolutionEvaluationTask task = processingTask(Category.GREEDY);
        List<Keyword> keywords = List.of(new Keyword(1L, "해시맵"), new Keyword(1L, "순회"));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("DP")));
        when(keywordRepository.findByProblemIdOrderById(1L)).thenReturn(keywords);
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());

        // When
        EvaluationInput input = service.prepare(5L);

        // Then
        assertThat(input.request().category()).isEqualTo("DP");
        assertThat(input.request().problemTitle()).isEqualTo("제목");
        assertThat(input.request().naturalSolution()).isEqualTo("이렇게 풉니다");
        assertThat(input.request().solutionKeywords()).containsExactly("해시맵", "순회");
        assertThat(input.keywords()).isEqualTo(keywords);
    }

    @Test
    void complete_createsSubmissionWithRequestTime_andSucceedsTask_onFirstSubmission() {
        // Given
        SolutionEvaluationTask task = processingTask(Category.ARRAY);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(any())).thenAnswer(invocation -> {
            ApproachSubmission saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 501L);
            return saved;
        });
        List<Keyword> keywords = List.of(new Keyword(1L, "해시맵"), new Keyword(1L, "순회"));

        // When
        service.complete(5L, keywords, aiResult("해시맵"));

        // Then
        ArgumentCaptor<ApproachSubmission> captor = ArgumentCaptor.forClass(ApproachSubmission.class);
        verify(approachSubmissionRepository).save(captor.capture());
        ApproachSubmission submission = captor.getValue();
        assertThat(submission.getSubmittedCount()).isEqualTo(1);
        assertThat(submission.getSubmittedAt()).isEqualTo(REQUESTED_AT);
        assertThat(submission.getCategoryResult()).isTrue();
        assertThat(submission.getTotalScore()).isEqualTo(85);
        assertThat(submission.getKeywordResults())
                .containsExactly(new KeywordResult("해시맵", true), new KeywordResult("순회", false));
        verify(approachSubmissionRepository, never()).findForUpdateByUserIdAndProblemId(any(), any());
        verify(approachSubmissionService).applyFirstSolveRewards(submission, false, 10L, Difficulty.LV1, Category.ARRAY);
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.SUCCEEDED);
        assertThat(task.getSubmissionId()).isEqualTo(501L);
        assertThat(task.getActiveSlot()).isNull();
    }

    @Test
    void complete_setsCategoryResultFalse_whenSelectedCategoryIsWrong() {
        // Given
        SolutionEvaluationTask task = processingTask(Category.GREEDY);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("DP")));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.complete(5L, List.of(), aiResult());

        // Then
        ArgumentCaptor<ApproachSubmission> captor = ArgumentCaptor.forClass(ApproachSubmission.class);
        verify(approachSubmissionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoryResult()).isFalse();
    }

    @Test
    void complete_overwritesExistingSubmission_andIncreasesCount_onResubmission() {
        // Given
        SolutionEvaluationTask task = processingTask(Category.GRAPH);
        ApproachSubmission existing = new ApproachSubmission(10L, 1L, Category.ARRAY, "이전 풀이", false, 20, "이전 피드백");
        ReflectionTestUtils.setField(existing, "id", 501L);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("GRAPH")));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));
        when(approachSubmissionRepository.findForUpdateByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));

        // When
        service.complete(5L, List.of(), aiResult());

        // Then
        assertThat(existing.getSubmittedCount()).isEqualTo(2);
        assertThat(existing.getNaturalSolution()).isEqualTo("이렇게 풉니다");
        assertThat(existing.getSubmittedAt()).isEqualTo(REQUESTED_AT);
        assertThat(existing.getCategoryResult()).isTrue();
        verify(approachSubmissionRepository, never()).save(any());
        verify(approachSubmissionService).applyFirstSolveRewards(existing, false, 10L, Difficulty.LV1, Category.GRAPH);
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.SUCCEEDED);
        assertThat(task.getSubmissionId()).isEqualTo(501L);
    }

    @Test
    void complete_passesAlreadySolvedTrue_whenUserSolvedBefore() {
        // Given
        SolutionEvaluationTask task = processingTask(Category.ARRAY);
        ApproachSubmission existing = new ApproachSubmission(10L, 1L, Category.ARRAY, "이전 풀이", true, 90, "이전 피드백");
        existing.markAsSolved();
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));
        when(approachSubmissionRepository.findForUpdateByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));

        // When
        service.complete(5L, List.of(), aiResult());

        // Then
        verify(approachSubmissionService).applyFirstSolveRewards(existing, true, 10L, Difficulty.LV1, Category.ARRAY);
    }

    @Test
    void complete_throws429_andKeepsTaskProcessing_whenLimitAlreadyReached() {
        // Given
        SolutionEvaluationTask task = processingTask(Category.ARRAY);
        ApproachSubmission existing = new ApproachSubmission(10L, 1L, Category.ARRAY, "이전 풀이", false, 20, "이전 피드백");
        for (int i = 1; i < ApproachSubmission.MAX_SUBMISSION_COUNT; i++) {
            existing.resubmit(Category.ARRAY, "이전 풀이", false, 20, "이전 피드백");
        }
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));
        when(approachSubmissionRepository.findForUpdateByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));

        // When & Then
        assertThatThrownBy(() -> service.complete(5L, List.of(), aiResult()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(existing.getSubmittedCount()).isEqualTo(ApproachSubmission.MAX_SUBMISSION_COUNT);
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PROCESSING);
        verify(approachSubmissionService, never()).applyFirstSolveRewards(any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void complete_throwsNotFound_whenTaskMissing() {
        // Given
        when(taskRepository.findById(5L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.complete(5L, List.of(), aiResult()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("solution_evaluation_not_found");
        verifyNoInteractions(approachSubmissionRepository, approachSubmissionService);
    }

    @Test
    void fail_marksTaskFailed_andReleasesActiveSlot() {
        // Given
        SolutionEvaluationTask task = processingTask(Category.ARRAY);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        // When
        service.fail(5L, "ai_server_unavailable", "ai_server_unavailable");

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.FAILED);
        assertThat(task.getErrorCode()).isEqualTo("ai_server_unavailable");
        assertThat(task.getActiveSlot()).isNull();
        assertThat(task.getCompletedAt()).isEqualTo(NOW);
        verify(approachSubmissionRepository, never()).save(any());
        verify(approachSubmissionService, never()).applyFirstSolveRewards(any(), eq(true), any(), any(), any());
    }
}
