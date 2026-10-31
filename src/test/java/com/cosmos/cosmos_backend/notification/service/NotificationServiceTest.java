package com.cosmos.cosmos_backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.notification.domain.Notification;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import com.cosmos.cosmos_backend.notification.dto.response.NotificationListResponse;
import com.cosmos.cosmos_backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 3, 30, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 30, 6);

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SolutionEvaluationTaskRepository evaluationTaskRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-19T03:30:06Z"), ZoneId.of("Asia/Seoul"));
        service = new NotificationService(notificationRepository, evaluationTaskRepository, fixedClock);
    }

    private Notification notification(Long id, NotificationType type, LocalDateTime createdAt) {
        Notification notification = new Notification(42L, type, 101L, 9000L + id, "문구", createdAt);
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @Test
    void createEvaluationNotification_savesDoneNotification_withTitleInMessage() {
        // When
        service.createEvaluationNotification(42L, 101L, 9001L, NotificationType.NL_EVALUATION_DONE, "두 수의 합");

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getProblemId()).isEqualTo(101L);
        assertThat(saved.getEvaluationTaskId()).isEqualTo(9001L);
        assertThat(saved.getType()).isEqualTo(NotificationType.NL_EVALUATION_DONE);
        assertThat(saved.getMessage()).isEqualTo("'두 수의 합' 평가가 완료됐어요.");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void createEvaluationNotification_savesFailedNotification_withResubmitGuide() {
        // When
        service.createEvaluationNotification(42L, 102L, 9002L, NotificationType.NL_EVALUATION_FAILED, "괄호 검사");

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.NL_EVALUATION_FAILED);
        assertThat(captor.getValue().getMessage()).isEqualTo("'괄호 검사' 평가를 완료하지 못했어요. 다시 제출해 주세요.");
    }

    @Test
    void getNotifications_returnsRecentNotifications_andUnreadCount() {
        // Given
        Notification newer = notification(13L, NotificationType.NL_EVALUATION_FAILED, CREATED_AT.plusMinutes(1));
        Notification older = notification(12L, NotificationType.NL_EVALUATION_DONE, CREATED_AT);
        older.markRead(CREATED_AT.plusSeconds(30));
        when(notificationRepository.findOfLatestTasks(42L, Limit.of(10))).thenReturn(List.of(newer, older));
        when(notificationRepository.countUnreadOfLatestTasks(42L)).thenReturn(15L);

        // When
        NotificationListResponse response = service.getNotifications(42L);

        // Then
        assertThat(response.unreadCount()).isEqualTo(15);
        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications().get(0).id()).isEqualTo(13L);
        assertThat(response.notifications().get(0).type()).isEqualTo(NotificationType.NL_EVALUATION_FAILED);
        assertThat(response.notifications().get(0).isRead()).isFalse();
        assertThat(response.notifications().get(1).id()).isEqualTo(12L);
        assertThat(response.notifications().get(1).isRead()).isTrue();
        assertThat(response.notifications().get(1).createdAt()).isEqualTo(Instant.parse("2026-10-19T03:30:00Z"));
    }

    @Test
    void getNotifications_returnsEmptyList_whenNoNotifications() {
        // Given
        when(notificationRepository.findOfLatestTasks(42L, Limit.of(10))).thenReturn(List.of());
        when(notificationRepository.countUnreadOfLatestTasks(42L)).thenReturn(0L);

        // When
        NotificationListResponse response = service.getNotifications(42L);

        // Then
        assertThat(response.unreadCount()).isZero();
        assertThat(response.notifications()).isEmpty();
    }

    @Test
    void getNotifications_reportsPendingEvaluation_whenActiveTaskExists() {
        // Given
        when(notificationRepository.findOfLatestTasks(42L, Limit.of(10))).thenReturn(List.of());
        when(notificationRepository.countUnreadOfLatestTasks(42L)).thenReturn(0L);
        when(evaluationTaskRepository.existsByUserIdAndActiveSlot(42L, 1)).thenReturn(true);

        // When
        NotificationListResponse response = service.getNotifications(42L);

        // Then
        assertThat(response.hasPendingEvaluation()).isTrue();
    }

    @Test
    void findIdByEvaluationTaskId_returnsNotificationId_orEmptyWhenNotCreatedYet() {
        // Given
        Notification notification = notification(12L, NotificationType.NL_EVALUATION_DONE, CREATED_AT);
        when(notificationRepository.findByEvaluationTaskId(9012L)).thenReturn(Optional.of(notification));
        when(notificationRepository.findByEvaluationTaskId(9999L)).thenReturn(Optional.empty());

        // When & Then
        assertThat(service.findIdByEvaluationTaskId(9012L)).contains(12L);
        assertThat(service.findIdByEvaluationTaskId(9999L)).isEmpty();
    }

    @Test
    void markRead_recordsReadTime() {
        // Given
        Notification notification = notification(12L, NotificationType.NL_EVALUATION_DONE, CREATED_AT);
        when(notificationRepository.findByIdAndUserId(12L, 42L)).thenReturn(Optional.of(notification));

        // When
        service.markRead(42L, 12L);

        // Then
        assertThat(notification.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void markRead_keepsFirstReadTime_whenAlreadyRead() {
        // Given
        Notification notification = notification(12L, NotificationType.NL_EVALUATION_DONE, CREATED_AT);
        notification.markRead(CREATED_AT.plusSeconds(1));
        when(notificationRepository.findByIdAndUserId(12L, 42L)).thenReturn(Optional.of(notification));

        // When
        service.markRead(42L, 12L);

        // Then
        assertThat(notification.getReadAt()).isEqualTo(CREATED_AT.plusSeconds(1));
    }

    @Test
    void markRead_throwsNotFound_whenNotificationMissingOrNotMine() {
        // Given
        when(notificationRepository.findByIdAndUserId(12L, 42L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.markRead(42L, 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("notification_not_found")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
