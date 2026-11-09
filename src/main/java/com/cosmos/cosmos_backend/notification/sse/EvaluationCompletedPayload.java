package com.cosmos.cosmos_backend.notification.sse;

import com.cosmos.cosmos_backend.notification.event.EvaluationCompletedEvent;

/** 브라우저로 나가는 평가 완료 이벤트 내용. userId는 뺌. */
public record EvaluationCompletedPayload(
        String eventId,
        Long evaluationTaskId,
        String status,
        Long notificationId
) {

    static EvaluationCompletedPayload from(EvaluationCompletedEvent event) {
        return new EvaluationCompletedPayload(
                event.eventId(), event.evaluationTaskId(), event.status(), event.notificationId());
    }
}
