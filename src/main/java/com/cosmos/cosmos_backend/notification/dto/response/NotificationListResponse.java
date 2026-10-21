package com.cosmos.cosmos_backend.notification.dto.response;

import com.cosmos.cosmos_backend.approach.dto.response.UtcInstants;
import com.cosmos.cosmos_backend.notification.domain.Notification;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;

/** 알림 목록 응답. 안 읽은 전체 개수와 최근 알림 목록. */
public record NotificationListResponse(long unreadCount, List<NotificationItem> notifications) {

    /** 알림 1건. */
    public record NotificationItem(
            Long id,
            NotificationType type,
            Long evaluationTaskId,
            Long problemId,
            String message,
            Boolean isRead,
            Instant createdAt
    ) {

        // 엔티티를 응답용 알림 1건으로 변환
        static NotificationItem from(Notification notification) {
            return new NotificationItem(
                    notification.getId(),
                    notification.getType(),
                    notification.getEvaluationTaskId(),
                    notification.getProblemId(),
                    notification.getMessage(),
                    notification.isRead(),
                    UtcInstants.of(notification.getCreatedAt()));
        }
    }

    /** 안 읽은 개수와 알림 엔티티 목록으로 응답을 조립. */
    public static NotificationListResponse of(long unreadCount, List<Notification> notifications) {
        return new NotificationListResponse(unreadCount,
                notifications.stream().map(NotificationItem::from).toList());
    }
}
