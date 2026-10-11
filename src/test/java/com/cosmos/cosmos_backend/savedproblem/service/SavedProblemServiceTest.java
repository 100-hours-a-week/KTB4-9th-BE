package com.cosmos.cosmos_backend.savedproblem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.repository.SavedProblemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        boolean created = service().save(7L, 1L);

        // Then: 새로 저장했다고 알려주고, 실제로 저장을 요청함
        assertThat(created).isTrue();
        verify(savedProblemRepository).save(any(SavedProblem.class));
    }

    @Test
    void save_doesNotSaveAgainAndReturnsFalse_whenAlreadySaved() {
        // Given: 이미 저장한 상태
        when(problemRepository.existsById(1L)).thenReturn(true);
        when(savedProblemRepository.existsByUserIdAndProblemId(7L, 1L)).thenReturn(true);

        // When
        boolean created = service().save(7L, 1L);

        // Then: false를 돌려주고 저장은 하지 않음
        assertThat(created).isFalse();
        verify(savedProblemRepository, never()).save(any());
    }

    @Test
    void save_throwsNotFound_whenProblemMissing() {
        // Given: 없는 문제
        when(problemRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> service().save(7L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("problem_not_found");
        verify(savedProblemRepository, never()).save(any());
    }

    @Test
    void unsave_deletesSavedRecord() {
        // When
        service().unsave(7L, 1L);

        // Then: 해당 사용자·문제의 저장 기록을 지우도록 요청함
        verify(savedProblemRepository).deleteByUserIdAndProblemId(7L, 1L);
    }
}
