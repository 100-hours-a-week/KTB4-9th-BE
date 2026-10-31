package com.cosmos.cosmos_backend.notification.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 알림이 커밋된 뒤 평가 완료 이벤트를 이벤트 버스로 전달. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationCompletedListener {

    private final EvaluationEventBus eventBus;
    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(EvaluationCompletedEvent event) {
        try {
            // 1. 커밋이 끝난 뒤 이벤트 버스로 전달
            eventBus.publish(event);
        } catch (Exception e) {
            // 2. 전송이 실패해도 평가는 그대로 둠 (로그와 카운터만 남김)
            log.warn("평가 완료 이벤트 전송 실패 evaluationTaskId={}", event.evaluationTaskId(), e);
            meterRegistry.counter("evaluation.event.publish.failure").increment();
        }
    }
}
