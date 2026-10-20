package com.cosmos.cosmos_backend.approach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.ranking.service.UserPointService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
class ApproachSubmissionServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ApproachSubmissionRepository approachSubmissionRepository;

    @Mock
    private ActivityRecordRepository activityRecordRepository;

    @Mock
    private UserPointService userPointService;

    private ApproachSubmissionService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new ApproachSubmissionService(problemRepository, approachSubmissionRepository, activityRecordRepository, userPointService, fixedClock);
    }

    private Problem problem(String category) {
        return new Problem(Difficulty.LV1, Category.valueOf(category), "제목", "내용", "입력 형식", "출력 형식", List.<AiProblemsCreateRequestDto.InputConstraints>of(), "선정 배경");
    }

    @Test
    void applyFirstSolveRewards_marksSolvedAndRecordsActivityAndPoints_onFirstCorrectAnswer() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(10L, 1L, Category.ARRAY, "풀이", true, 90, "좋아요");
        when(activityRecordRepository.findByUserIdAndActivityDate(10L, LocalDate.of(2026, 9, 28))).thenReturn(Optional.empty());

        // When
        service.applyFirstSolveRewards(submission, false, 10L, Difficulty.LV2, Category.ARRAY);

        // Then
        assertThat(submission.getIsSolved()).isTrue();
        ArgumentCaptor<ActivityRecord> captor = ArgumentCaptor.forClass(ActivityRecord.class);
        verify(activityRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getCorrectProblemCount()).isEqualTo(1L);
        assertThat(captor.getValue().getActivityDate()).isEqualTo(LocalDate.of(2026, 9, 28));
        verify(userPointService).updatePoint(10L, Difficulty.LV2, Category.ARRAY);
    }

    @Test
    void applyFirstSolveRewards_increasesExistingActivityRecord_whenTodayRecordExists() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(10L, 1L, Category.ARRAY, "풀이", true, 85, "좋아요");
        ActivityRecord today = new ActivityRecord(10L, LocalDate.of(2026, 9, 28), 2L);
        when(activityRecordRepository.findByUserIdAndActivityDate(10L, LocalDate.of(2026, 9, 28))).thenReturn(Optional.of(today));

        // When
        service.applyFirstSolveRewards(submission, false, 10L, Difficulty.LV1, Category.ARRAY);

        // Then
        assertThat(today.getCorrectProblemCount()).isEqualTo(3L);
        verify(activityRecordRepository, never()).save(any());
        verify(userPointService).updatePoint(10L, Difficulty.LV1, Category.ARRAY);
    }

    @Test
    void applyFirstSolveRewards_doesNothing_whenAlreadySolvedBefore() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(10L, 1L, Category.ARRAY, "풀이", true, 95, "좋아요");

        // When
        service.applyFirstSolveRewards(submission, true, 10L, Difficulty.LV1, Category.ARRAY);

        // Then
        assertThat(submission.getIsSolved()).isFalse();
        verifyNoInteractions(activityRecordRepository, userPointService);
    }

    @Test
    void applyFirstSolveRewards_doesNothing_whenCategoryIsWrong() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(10L, 1L, Category.DP, "풀이", false, 95, "아쉬워요");

        // When
        service.applyFirstSolveRewards(submission, false, 10L, Difficulty.LV1, Category.DP);

        // Then
        assertThat(submission.getIsSolved()).isFalse();
        verifyNoInteractions(activityRecordRepository, userPointService);
    }

    @Test
    void applyFirstSolveRewards_doesNothing_whenScoreIsBelowThreshold() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(10L, 1L, Category.ARRAY, "풀이", true, 84, "아쉬워요");

        // When
        service.applyFirstSolveRewards(submission, false, 10L, Difficulty.LV1, Category.ARRAY);

        // Then
        assertThat(submission.getIsSolved()).isFalse();
        verifyNoInteractions(activityRecordRepository, userPointService);
    }

    @Test
    void mergeKeywordJudgements_missingIsFalse_unknownIsDropped() {
        // Given
        List<Keyword> ours = List.of(new Keyword(1L, "정렬"), new Keyword(1L, "이분 탐색"), new Keyword(1L, "투 포인터"));
        List<AiEvaluationResult.KeywordJudgement> ai = List.of(
                new AiEvaluationResult.KeywordJudgement("정렬", true),
                new AiEvaluationResult.KeywordJudgement("AI가 준 모르는 키워드", true)
        );

        // When
        var merged = ApproachSubmissionService.mergeKeywordJudgements(ours, ai);

        // Then
        assertThat(merged).containsExactly(
                new AiEvaluationResult.KeywordJudgement("정렬", true),
                new AiEvaluationResult.KeywordJudgement("이분 탐색", false),
                new AiEvaluationResult.KeywordJudgement("투 포인터", false)
        );
    }

    @Test
    void buildAiRequest_sendsProblemAnswerCategory_notSelectedCategory() {
        // When
        AiEvaluationRequest request = ApproachSubmissionService.buildAiRequest(problem("DP"), "풀이", List.of(new Keyword(1L, "점화식")), List.of());

        // Then
        assertThat(request.category()).isEqualTo("DP");
        assertThat(request.naturalSolution()).isEqualTo("풀이");
        assertThat(request.solutionKeywords()).containsExactly("점화식");
    }

    @Test
    void limitExceeded_buildsTooManyRequests_withNestedUsageData() {
        // When
        BusinessException exception = ApproachSubmissionService.limitExceeded(1L, 5);

        // Then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exception.getMessage()).isEqualTo("solution_submission_limit_exceeded");
        assertThat(exception.getData()).isEqualTo(
                new ApproachSubmitResponse.SubmissionLimitExceededData(1L, new ApproachSubmitResponse.SubmissionUsage(5, 5, 0)));
    }

    @Test
    void getCategoryAnswer_returnsAnswer_whenUserHasSubmitted() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(10L, 1L, Category.DP, "풀이", false, 40, "아쉬워요");
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(submission));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));

        // When
        CategoryAnswerResponse response = service.getCategoryAnswer(10L, 1L);

        // Then
        assertThat(response.problemId()).isEqualTo(1L);
        assertThat(response.correctCategory()).isEqualTo("ARRAY");
        assertThat(response.categorySelectReason()).isEqualTo("선정 배경");
    }

    @Test
    void getCategoryAnswer_throws403_whenNeverSubmitted() {
        // Given
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getCategoryAnswer(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("category_answer_not_available");
        verifyNoInteractions(problemRepository);
    }
}
