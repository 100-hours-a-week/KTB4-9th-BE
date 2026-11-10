package com.cosmos.cosmos_backend.approach.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService.EvaluationInput;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationRelayService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationRelayTest {

    @Mock
    private SolutionEvaluationRelayService relayService;

    @Mock
    private SolutionEvaluationExecutionService executionService;

    @Mock
    private EvaluationRequestPublisher publisher;

    private EvaluationRelay relay;

    private final AiEvaluationRequest request =
            new AiEvaluationRequest("제목", "내용", "ARRAY", "이유", List.of(), List.of(), List.of(), "풀이");

    @BeforeEach
    void setUp() {
        EvaluationMqProperties properties = new EvaluationMqProperties(
                true, "evaluation.request", "evaluation.result", "evaluation.request.dlq", Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofMinutes(2), Duration.ofSeconds(1), 50, Duration.ofSeconds(5));
        relay = new EvaluationRelay(relayService, executionService, publisher, properties);
    }

    // 작업 id로 prepare를 하면 같은 요청이 나오도록 설정
    private void prepareReturnsRequest(Long... taskIds) {
        for (Long taskId : taskIds) {
            when(executionService.prepare(taskId)).thenReturn(new EvaluationInput(request, List.of()));
        }
    }

    @Test
    void runOnce_publishesEachClaimedTask_andMarksPublished() {
        // Given
        when(relayService.claimForPublish(50)).thenReturn(List.of(1L, 2L));
        prepareReturnsRequest(1L, 2L);
        when(publisher.publish(any(Long.class), any(AiEvaluationRequest.class))).thenReturn(true);

        // When
        relay.runOnce();

        // Then
        InOrder order = inOrder(publisher, relayService);
        order.verify(publisher).publish(1L, request);
        order.verify(relayService).markPublished(1L);
        order.verify(publisher).publish(2L, request);
        order.verify(relayService).markPublished(2L);
    }

    @Test
    void runOnce_stopsRemainingTasks_whenPublishFails() {
        // Given
        when(relayService.claimForPublish(50)).thenReturn(List.of(1L, 2L, 3L));
        prepareReturnsRequest(1L, 2L);
        when(publisher.publish(1L, request)).thenReturn(true);
        when(publisher.publish(2L, request)).thenReturn(false);

        // When
        relay.runOnce();

        // Then
        verify(relayService).markPublished(1L);
        verify(relayService, never()).markPublished(2L);
        verify(publisher, never()).publish(3L, request);
    }

    @Test
    void runOnce_skipsOnlyThatTask_whenPrepareFails() {
        // Given
        when(relayService.claimForPublish(50)).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("problem missing")).when(executionService).prepare(1L);
        prepareReturnsRequest(2L);
        when(publisher.publish(2L, request)).thenReturn(true);

        // When
        relay.runOnce();

        // Then
        verify(publisher, never()).publish(1L, request);
        verify(relayService, never()).markPublished(1L);
        verify(relayService).markPublished(2L);
    }

    @Test
    void runOnce_failsExpiredPendingBeforeClaiming() {
        // Given
        when(relayService.findExpiredPendingIds()).thenReturn(List.of(9L));
        when(relayService.claimForPublish(50)).thenReturn(List.of());

        // When
        relay.runOnce();

        // Then
        InOrder order = inOrder(relayService);
        order.verify(relayService).failExpiredPending(9L);
        order.verify(relayService).claimForPublish(50);
    }

    @Test
    void runOnce_keepsGoing_whenFailingOneExpiredTaskThrows() {
        // Given
        when(relayService.findExpiredPendingIds()).thenReturn(List.of(8L, 9L));
        doThrow(new IllegalStateException("db error")).when(relayService).failExpiredPending(8L);
        when(relayService.claimForPublish(50)).thenReturn(List.of());

        // When
        relay.runOnce();

        // Then
        verify(relayService).failExpiredPending(9L);
        verify(relayService).claimForPublish(50);
    }

    @Test
    void runOnce_doesNothing_whenNoTasks() {
        // Given
        when(relayService.claimForPublish(50)).thenReturn(List.of());

        // When
        relay.runOnce();

        // Then
        verify(publisher, never()).publish(anyLong(), any());
        verify(relayService, never()).markPublished(anyLong());
    }
}
