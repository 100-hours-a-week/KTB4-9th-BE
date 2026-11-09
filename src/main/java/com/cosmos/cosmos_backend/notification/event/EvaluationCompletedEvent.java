package com.cosmos.cosmos_backend.notification.event;

/** 평가 완료 이벤트. 서버 간 전달 메시지에도 같은 값을 씀. */
public record EvaluationCompletedEvent(
        String eventId,
        Long userId,
        Long evaluationTaskId,
        String status,
        Long notificationId
) {
}
