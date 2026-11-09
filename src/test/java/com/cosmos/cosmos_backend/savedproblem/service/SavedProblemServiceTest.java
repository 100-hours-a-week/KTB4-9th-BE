package com.cosmos.cosmos_backend.savedproblem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblemStatus;
import com.cosmos.cosmos_backend.savedproblem.dto.SavedProblemRow;
import com.cosmos.cosmos_backend.savedproblem.dto.response.SavedProblemResponse;
import com.cosmos.cosmos_backend.savedproblem.repository.SavedProblemRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SavedProblemServiceTest {

    @Mock
    private SavedProblemRepository savedProblemRepository;

    @Mock
    private ProblemRepository problemRepository;

    private SavedProblemService service() {
        return new SavedProblemService(savedProblemRepository, problemRepository);
    }

    @Test
    void save_savesAndReturnsTrue_whenNotSavedYet() {
        // Given: 문제는 있고, 아직 저장하지 않은 상태
        when(problemRepository.existsById(1L)).thenReturn(true);
        when(savedProblemRepository.existsByUserIdAndProblemId(7L, 1L)).thenReturn(false);

        // When
        boolean created = service().save(7L, 1L, true);

        // Then: 새로 저장했다고 알려주고, 저장 당시 categoryKnown을 담아 저장을 요청함
        assertThat(created).isTrue();
        ArgumentCaptor<SavedProblem> captor = ArgumentCaptor.forClass(SavedProblem.class);
        verify(savedProblemRepository).save(captor.capture());
        assertThat(captor.getValue().isCategoryKnown()).isTrue();
    }

    @Test
    void save_doesNotSaveAgainAndReturnsFalse_whenAlreadySaved() {
        // Given: 이미 저장한 상태
        when(problemRepository.existsById(1L)).thenReturn(true);
        when(savedProblemRepository.existsByUserIdAndProblemId(7L, 1L)).thenReturn(true);

        // When
        boolean created = service().save(7L, 1L, true);

        // Then: false를 돌려주고 저장은 하지 않음
        assertThat(created).isFalse();
        verify(savedProblemRepository, never()).save(any());
    }

    @Test
    void save_throwsNotFound_whenProblemMissing() {
        // Given: 없는 문제
        when(problemRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> service().save(7L, 999L, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("problem_not_found");
        verify(savedProblemRepository, never()).save(any());
    }

    @Test
    void getSavedProblems_mapsRowsToResponsesWithStatus() {
        // Given
        LocalDateTime savedAt = LocalDateTime.of(2026, 10, 8, 12, 0);
        when(savedProblemRepository.findSavedProblems(7L, null)).thenReturn(List.of(
                new SavedProblemRow(1L, "미제출 문제", Difficulty.LV1, Category.ARRAY, savedAt, null, null, false, null, null),
                new SavedProblemRow(2L, "풀이 중 문제", Difficulty.LV2, Category.DP, savedAt, false, "점화식으로 푼다", false, false, 1),
                new SavedProblemRow(3L, "정답 문제", Difficulty.LV3, Category.ARRAY, savedAt, true, "투 포인터로 푼다", false, true, 1)
        ));

        // When
        List<SavedProblemResponse> responses = service().getSavedProblems(7L, null);

        // Then
        assertThat(responses).extracting(SavedProblemResponse::status)
                .containsExactly(SavedProblemStatus.NOT_SUBMITTED, SavedProblemStatus.IN_PROGRESS, SavedProblemStatus.CORRECT);
        assertThat(responses).extracting(SavedProblemResponse::solution)
                .containsExactly(null, "점화식으로 푼다", "투 포인터로 푼다");
    }

    @Test
    void getSavedProblems_hidesCategory_unlessKnownOrCorrectOrLimitReached() {
        // Given: categoryKnown, categoryResult, submittedCount 조합별 저장 문제
        LocalDateTime savedAt = LocalDateTime.of(2026, 10, 8, 12, 0);
        when(savedProblemRepository.findSavedProblems(7L, null)).thenReturn(List.of(
                new SavedProblemRow(1L, "카테고리를 알던 문제", Difficulty.LV1, Category.ARRAY, savedAt, null, null, true, null, null),
                new SavedProblemRow(2L, "카테고리를 맞춘 랜덤 문제", Difficulty.LV1, Category.DP, savedAt, false, "풀이", false, true, 1),
                new SavedProblemRow(3L, "한도를 다 쓴 랜덤 문제", Difficulty.LV1, Category.TREE, savedAt, false, "풀이", false, false, 5),
                new SavedProblemRow(4L, "한도가 남은 랜덤 문제", Difficulty.LV1, Category.GRAPH, savedAt, false, "풀이", false, false, 4),
                new SavedProblemRow(5L, "제출 안 한 랜덤 문제", Difficulty.LV1, Category.MATH, savedAt, null, null, false, null, null)
        ));

        // When
        List<SavedProblemResponse> responses = service().getSavedProblems(7L, null);

        // Then: 알던 문제, 맞춘 문제, 한도를 다 쓴 문제만 카테고리가 보임
        assertThat(responses).extracting(SavedProblemResponse::category)
                .containsExactly(Category.ARRAY, Category.DP, Category.TREE, null, null);
    }

    @Test
    void unsave_deletesSavedRecord() {
        // When
        service().unsave(7L, 1L);

        // Then: 해당 사용자·문제의 저장 기록을 지우도록 요청함
        verify(savedProblemRepository).deleteByUserIdAndProblemId(7L, 1L);
    }
}
