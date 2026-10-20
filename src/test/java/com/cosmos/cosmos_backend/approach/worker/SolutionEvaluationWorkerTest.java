package com.cosmos.cosmos_backend.approach.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationClient;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService.EvaluationInput;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.Keyword;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SolutionEvaluationWorkerTest {

    @Mock
    private SolutionEvaluationExecutionService executionService;

    @Mock
    private AiEvaluationClient aiEvaluationClient;

    private SolutionEvaluationWorker worker;

    private final AiEvaluationRequest request = new AiEvaluationRequest("제목", "내용", "ARRAY", "선정 배경", List.of(), List.of(), List.of(), "풀이");
    private final List<Keyword> keywords = List.of(new Keyword(1L, "해시맵"));
    private final AiEvaluationResult result = new AiEvaluationResult(85, "좋아요", List.of());

    @BeforeEach
    void setUp() {
        worker = new SolutionEvaluationWorker(executionService, aiEvaluationClient);
    }

    @Test
    void runUntilEmpty_doesNothing_whenNoTask() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.empty());

        // When
        worker.runUntilEmpty();

        // Then
        verifyNoInteractions(aiEvaluationClient);
        verify(executionService, never()).prepare(any());
    }

    @Test
    void runUntilEmpty_evaluatesAndCompletes_whenAiSucceeds() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(5L), Optional.empty());
        when(executionService.prepare(5L)).thenReturn(new EvaluationInput(request, keywords));
        when(aiEvaluationClient.evaluate(request)).thenReturn(result);

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).complete(5L, keywords, result);
        verify(executionService, never()).fail(any(), any(), any());
    }

    @Test
    void runUntilEmpty_marksFailedWithErrorCode_whenAiFails() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(5L), Optional.empty());
        when(executionService.prepare(5L)).thenReturn(new EvaluationInput(request, keywords));
        when(aiEvaluationClient.evaluate(request))
                .thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable"));

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).fail(5L, "ai_server_unavailable", "ai_server_unavailable");
        verify(executionService, never()).complete(any(), any(), any());
    }

    @Test
    void runUntilEmpty_marksFailedWithInternalError_whenUnexpectedExceptionIsThrown() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(5L), Optional.empty());
        when(executionService.prepare(5L)).thenReturn(new EvaluationInput(request, keywords));
        when(aiEvaluationClient.evaluate(request)).thenReturn(result);
        doThrow(new IllegalStateException("저장 오류")).when(executionService).complete(5L, keywords, result);

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).fail(5L, "internal_error", "IllegalStateException");
    }

    @Test
    void runUntilEmpty_processesAllTasks_andContinuesAfterOneFails() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(1L), Optional.of(2L), Optional.of(3L), Optional.empty());
        when(executionService.prepare(any())).thenReturn(new EvaluationInput(request, keywords));
        when(aiEvaluationClient.evaluate(request))
                .thenReturn(result)
                .thenThrow(new BusinessException(HttpStatus.GATEWAY_TIMEOUT, "evaluation_timeout"))
                .thenReturn(result);

        // When
        worker.runUntilEmpty();

        // Then
        verify(aiEvaluationClient, times(3)).evaluate(request);
        verify(executionService).complete(1L, keywords, result);
        verify(executionService).fail(2L, "evaluation_timeout", "evaluation_timeout");
        verify(executionService).complete(3L, keywords, result);
    }

    @Test
    void runUntilEmpty_doesNotThrow_whenSavingFailureAlsoFails() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(5L), Optional.empty());
        when(executionService.prepare(5L)).thenReturn(new EvaluationInput(request, keywords));
        when(aiEvaluationClient.evaluate(request))
                .thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable"));
        doThrow(new IllegalStateException("DB 오류")).when(executionService).fail(eq(5L), any(), any());

        // When & Then
        assertThatCode(() -> worker.runUntilEmpty()).doesNotThrowAnyException();
    }
}
