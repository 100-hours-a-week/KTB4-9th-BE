package com.cosmos.cosmos_backend.approach.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolutionEvaluationRecovererTest {

    @Mock
    private SolutionEvaluationExecutionService executionService;

    @Test
    void recover_recoversEveryStuckTask() {
        // Given
        when(executionService.findStuckTaskIds()).thenReturn(List.of(5L, 6L, 7L));
        SolutionEvaluationRecoverer recoverer = new SolutionEvaluationRecoverer(executionService);

        // When
        recoverer.recover();

        // Then
        verify(executionService).recoverStuck(5L);
        verify(executionService).recoverStuck(6L);
        verify(executionService).recoverStuck(7L);
    }

    @Test
    void recover_doesNothing_whenNoStuckTask() {
        // Given
        when(executionService.findStuckTaskIds()).thenReturn(List.of());
        SolutionEvaluationRecoverer recoverer = new SolutionEvaluationRecoverer(executionService);

        // When
        recoverer.recover();

        // Then
        verify(executionService, never()).recoverStuck(anyLong());
    }

    @Test
    void recover_continuesWithOtherTasks_whenOneRecoveryFails() {
        // Given
        when(executionService.findStuckTaskIds()).thenReturn(List.of(5L, 6L));
        doThrow(new IllegalStateException("DB 오류")).when(executionService).recoverStuck(5L);
        SolutionEvaluationRecoverer recoverer = new SolutionEvaluationRecoverer(executionService);

        // When & Then
        assertThatCode(recoverer::recover).doesNotThrowAnyException();
        verify(executionService).recoverStuck(6L);
    }
}
