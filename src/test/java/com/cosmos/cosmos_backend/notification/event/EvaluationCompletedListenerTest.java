package com.cosmos.cosmos_backend.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationCompletedListenerTest {

    private static final EvaluationCompletedEvent EVENT =
            new EvaluationCompletedEvent("evt-1", 42L, 9001L, "SUCCEEDED", 7001L);

    @Mock
    private EvaluationEventBus eventBus;

    private SimpleMeterRegistry meterRegistry;
    private EvaluationCompletedListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new EvaluationCompletedListener(eventBus, meterRegistry);
    }

    @Test
    void onCompleted_passesEventToBus() {
        // When
        listener.onCompleted(EVENT);

        // Then
        verify(eventBus).publish(EVENT);
    }

    @Test
    void onCompleted_swallowsException_andCountsFailure_whenBusFails() {
        // Given
        doThrow(new IllegalStateException("redis down")).when(eventBus).publish(EVENT);

        // When & Then
        assertThatCode(() -> listener.onCompleted(EVENT)).doesNotThrowAnyException();
        assertThat(meterRegistry.counter("evaluation.event.publish.failure").count()).isEqualTo(1.0);
    }
}
