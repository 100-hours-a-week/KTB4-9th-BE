package com.cosmos.cosmos_backend.notification.event;

import com.cosmos.cosmos_backend.notification.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 이벤트를 같은 서버의 SSE 레지스트리로 바로 전달. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "evaluation.event.transport", havingValue = "local", matchIfMissing = true)
public class LocalEvaluationEventBus implements EvaluationEventBus {

    private final SseEmitterRegistry registry;

    @Override
    public void publish(EvaluationCompletedEvent event) {
        registry.dispatch(event);
    }
}
