package com.cosmos.cosmos_backend.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cosmos.cosmos_backend.notification.domain.Notification;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class NotificationRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 30, 0);

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private TestEntityManager em;

    private Notification notification(Long userId, Long evaluationTaskId) {
        return new Notification(userId, NotificationType.NL_EVALUATION_DONE, 10L, evaluationTaskId,
                "'두 수의 합' 평가가 완료됐어요.", NOW);
    }

    @Test
    void save_andReload_keepsAllFields() {
        // Given
        Notification saved = repository.saveAndFlush(
                new Notification(1L, NotificationType.NL_EVALUATION_FAILED, 10L, 9001L, "실패 문구", NOW));
        em.clear();

        // When
        Notification loaded = repository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getType()).isEqualTo(NotificationType.NL_EVALUATION_FAILED);
        assertThat(loaded.getProblemId()).isEqualTo(10L);
        assertThat(loaded.getEvaluationTaskId()).isEqualTo(9001L);
        assertThat(loaded.getMessage()).isEqualTo("실패 문구");
        assertThat(loaded.getReadAt()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void save_throws_whenSameEvaluationTaskAlreadyHasNotification() {
        // Given
        repository.saveAndFlush(notification(1L, 9001L));

        // When & Then
        assertThatThrownBy(() -> repository.saveAndFlush(notification(1L, 9001L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findTop10_returnsNewestTenOfMine_andExcludesOthers() {
        // Given: 내 알림 11개와 다른 사용자 알림 1개
        for (long taskId = 1; taskId <= 11; taskId++) {
            repository.save(notification(1L, taskId));
        }
        repository.save(notification(2L, 100L));
        repository.flush();

        // When
        List<Notification> recent = repository.findTop10ByUserIdOrderByIdDesc(1L);

        // Then: 가장 오래된 1개(taskId 1)가 빠지고 최신순
        assertThat(recent).hasSize(10);
        assertThat(recent).extracting(Notification::getUserId).containsOnly(1L);
        assertThat(recent).extracting(Notification::getEvaluationTaskId)
                .containsExactly(11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L);
    }

    @Test
    void findTop10_includesReadNotifications() {
        // Given
        Notification read = repository.save(notification(1L, 1L));
        read.markRead(NOW.plusMinutes(1));
        repository.save(notification(1L, 2L));
        repository.flush();

        // When
        List<Notification> recent = repository.findTop10ByUserIdOrderByIdDesc(1L);

        // Then
        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(Notification::isRead).containsExactly(false, true);
    }

    @Test
    void countUnread_countsAllUnreadOfMine_evenBeyondTenShown() {
        // Given: 안 읽은 알림 12개 중 2개를 읽음 처리, 다른 사용자 알림 1개
        for (long taskId = 1; taskId <= 12; taskId++) {
            Notification notification = repository.save(notification(1L, taskId));
            if (taskId <= 2) {
                notification.markRead(NOW.plusMinutes(1));
            }
        }
        repository.save(notification(2L, 100L));
        repository.flush();

        // When & Then
        assertThat(repository.countByUserIdAndReadAtIsNull(1L)).isEqualTo(10);
        assertThat(repository.countByUserIdAndReadAtIsNull(3L)).isZero();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenNotificationBelongsToAnotherUser() {
        // Given
        Notification mine = repository.saveAndFlush(notification(1L, 1L));

        // When & Then
        assertThat(repository.findByIdAndUserId(mine.getId(), 1L)).isPresent();
        assertThat(repository.findByIdAndUserId(mine.getId(), 2L)).isEmpty();
    }
}
