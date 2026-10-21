package com.cosmos.cosmos_backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 3, 30, 0);
    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 10, 19, 3, 31, 0);

    private Notification notification() {
        return new Notification(1L, NotificationType.NL_EVALUATION_DONE, 10L, 9001L,
                "'두 수의 합' 평가가 완료됐어요.", CREATED_AT);
    }

    @Test
    void newNotification_isUnread_withGivenValues() {
        // When
        Notification notification = notification();

        // Then
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
        assertThat(notification.getUserId()).isEqualTo(1L);
        assertThat(notification.getType()).isEqualTo(NotificationType.NL_EVALUATION_DONE);
        assertThat(notification.getProblemId()).isEqualTo(10L);
        assertThat(notification.getEvaluationTaskId()).isEqualTo(9001L);
        assertThat(notification.getMessage()).isEqualTo("'두 수의 합' 평가가 완료됐어요.");
        assertThat(notification.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void markRead_recordsReadTime() {
        // Given
        Notification notification = notification();

        // When
        notification.markRead(READ_AT);

        // Then
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(READ_AT);
    }

    @Test
    void markRead_keepsFirstReadTime_whenCalledAgain() {
        // Given
        Notification notification = notification();
        notification.markRead(READ_AT);

        // When
        notification.markRead(READ_AT.plusMinutes(5));

        // Then
        assertThat(notification.getReadAt()).isEqualTo(READ_AT);
    }
}
