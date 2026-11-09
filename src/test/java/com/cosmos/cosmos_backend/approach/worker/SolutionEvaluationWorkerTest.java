package com.cosmos.cosmos_backend.approach.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationClient;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationFailedException;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.EvaluationFailureCode;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService.ClaimedTask;
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

    private void stubPrepare() {
        when(executionService.prepare(any())).thenReturn(new EvaluationInput(request, keywords));
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
        when(executionService.claimNext()).thenReturn(Optional.of(new ClaimedTask(5L, 1)), Optional.empty());
        stubPrepare();
        when(aiEvaluationClient.evaluate(request)).thenReturn(result);

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).complete(5L, 1, keywords, result);
        verify(executionService, never()).handleFailure(any(), anyInt(), any(), any(), anyBoolean());
    }

    @Test
    void runUntilEmpty_reportsRetryableFailure_whenAiTimesOut() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(new ClaimedTask(5L, 2)), Optional.empty());
        stubPrepare();
        when(aiEvaluationClient.evaluate(request)).thenThrow(new AiEvaluationFailedException(
                HttpStatus.GATEWAY_TIMEOUT, "evaluation_timeout", EvaluationFailureCode.AI_SERVER_TIMEOUT, true, null));

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).handleFailure(5L, 2, EvaluationFailureCode.AI_SERVER_TIMEOUT, "evaluation_timeout", true);
        verify(executionService, never()).complete(any(), anyInt(), any(), any());
    }

    @Test
    void runUntilEmpty_reportsNotRetryableFailure_withAiErrorCode_whenAiRejectsRequest() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(new ClaimedTask(5L, 1)), Optional.empty());
        stubPrepare();
        when(aiEvaluationClient.evaluate(request)).thenThrow(new AiEvaluationFailedException(
                HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable", EvaluationFailureCode.AI_REQUEST_REJECTED, false, "INVALID_CATEGORY"));

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).handleFailure(5L, 1, EvaluationFailureCode.AI_REQUEST_REJECTED, "INVALID_CATEGORY", false);
    }

    @Test
    void runUntilEmpty_doesNotRetry_whenBusinessRuleIsViolated() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(new ClaimedTask(5L, 1)), Optional.empty());
        stubPrepare();
        when(aiEvaluationClient.evaluate(request)).thenReturn(result);
        doThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "solution_submission_limit_exceeded"))
                .when(executionService).complete(5L, 1, keywords, result);

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).handleFailure(5L, 1, EvaluationFailureCode.INTERNAL_ERROR, "solution_submission_limit_exceeded", false);
    }

    @Test
    void runUntilEmpty_retries_whenUnexpectedExceptionIsThrown() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(new ClaimedTask(5L, 1)), Optional.empty());
        stubPrepare();
        when(aiEvaluationClient.evaluate(request)).thenReturn(result);
        doThrow(new IllegalStateException("저장 오류")).when(executionService).complete(5L, 1, keywords, result);

        // When
        worker.runUntilEmpty();

        // Then
        verify(executionService).handleFailure(5L, 1, EvaluationFailureCode.INTERNAL_ERROR, "IllegalStateException", true);
    }

    @Test
    void runUntilEmpty_processesAllTasks_andContinuesAfterOneFails() {
        // Given
        when(executionService.claimNext()).thenReturn(
                Optional.of(new ClaimedTask(1L, 1)), Optional.of(new ClaimedTask(2L, 1)),
                Optional.of(new ClaimedTask(3L, 1)), Optional.empty());
        stubPrepare();
        when(aiEvaluationClient.evaluate(request))
                .thenReturn(result)
                .thenThrow(new AiEvaluationFailedException(
                        HttpStatus.GATEWAY_TIMEOUT, "evaluation_timeout", EvaluationFailureCode.AI_SERVER_TIMEOUT, true, null))
                .thenReturn(result);

        // When
        worker.runUntilEmpty();

        // Then
        verify(aiEvaluationClient, times(3)).evaluate(request);
        verify(executionService).complete(1L, 1, keywords, result);
        verify(executionService).handleFailure(2L, 1, EvaluationFailureCode.AI_SERVER_TIMEOUT, "evaluation_timeout", true);
        verify(executionService).complete(3L, 1, keywords, result);
    }

    @Test
    void runUntilEmpty_doesNotThrow_whenRecordingFailureAlsoFails() {
        // Given
        when(executionService.claimNext()).thenReturn(Optional.of(new ClaimedTask(5L, 1)), Optional.empty());
        stubPrepare();
        when(aiEvaluationClient.evaluate(request)).thenThrow(new AiEvaluationFailedException(
                HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable", EvaluationFailureCode.AI_SERVER_UNAVAILABLE, true, null));
        doThrow(new IllegalStateException("DB 오류")).when(executionService).handleFailure(any(), anyInt(), any(), any(), anyBoolean());

        // When & Then
        assertThatCode(() -> worker.runUntilEmpty()).doesNotThrowAnyException();
    }
}
