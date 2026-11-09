package com.cosmos.cosmos_backend.notification.event;

import static org.mockito.Mockito.verify;

import com.cosmos.cosmos_backend.notification.sse.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalEvaluationEventBusTest {

    @Mock
    private SseEmitterRegistry registry;

    @InjectMocks
    private LocalEvaluationEventBus bus;

    @Test
    void publish_dispatchesToRegistry() {
        // Given
        EvaluationCompletedEvent event = new EvaluationCompletedEvent("evt-1", 42L, 9001L, "SUCCEEDED", 7001L);

        // When
        bus.publish(event);

        // Then
        verify(registry).dispatch(event);
    }
}
