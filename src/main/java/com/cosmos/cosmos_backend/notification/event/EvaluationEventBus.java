package com.cosmos.cosmos_backend.notification.event;

/** 평가 완료 이벤트를 SSE 레지스트리까지 전달. */
public interface EvaluationEventBus {

    void publish(EvaluationCompletedEvent event);
}
