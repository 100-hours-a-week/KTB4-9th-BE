package com.cosmos.cosmos_backend.approach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.domain.EvaluationStatus;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemConstraints;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ApproachSubmissionServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ApproachSubmissionRepository approachSubmissionRepository;

    private ApproachSubmissionService service() {
        return new ApproachSubmissionService(problemRepository, approachSubmissionRepository);
    }

    private Problem problem(String category) {
        return new Problem("LV1", category, "제목", "내용", "입력 형식", "출력 형식", new ProblemConstraints(List.of()), "선정 배경");
    }

    @Test
    void submit_createsNewSubmission_whenFirstTime() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ApproachSubmission result = service().submit(10L, 1L, "ARRAY", "이렇게 풉니다");

        // Then
        assertThat(result.getSubmittedCount()).isEqualTo(1);
        assertThat(result.getCategoryResult()).isTrue();
        assertThat(result.getEvaluationStatus()).isEqualTo(EvaluationStatus.PENDING);
    }

    @Test
    void submit_incrementsCountAndResetsScore_whenResubmitting() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("GRAPH")));
        ApproachSubmission existing = new ApproachSubmission(10L, 1L, Category.ARRAY, "이전 풀이", false);
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));

        // When
        ApproachSubmission result = service().submit(10L, 1L, "GRAPH", "새 풀이");

        // Then
        assertThat(result.getSubmittedCount()).isEqualTo(2);
        assertThat(result.getCategoryResult()).isTrue();
        assertThat(result.getTotalScore()).isNull();
        assertThat(result.getNaturalSolution()).isEqualTo("새 풀이");
    }

    @Test
    void submit_setsCategoryResultFalse_whenSelectedCategoryWrong() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("DP")));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ApproachSubmission result = service().submit(10L, 1L, "GREEDY", "이렇게 풉니다");

        // Then
        assertThat(result.getCategoryResult()).isFalse();
    }

    @Test
    void submit_throwsNotFound_whenProblemMissing() {
        // Given
        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service().submit(10L, 999L, "ARRAY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void submit_throwsBadRequest_whenSelectedCategoryInvalid() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));

        // When & Then
        assertThatThrownBy(() -> service().submit(10L, 1L, "NOT_A_CATEGORY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessage("invalid_selected_category");
    }
}
