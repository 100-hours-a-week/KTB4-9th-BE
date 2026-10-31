package com.cosmos.cosmos_backend.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.notification.domain.Notification;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

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

    // 최종 상태(활성 해제)가 된 평가 작업을 저장한다.
    private SolutionEvaluationTask finishedTask(Long userId, Long problemId) {
        SolutionEvaluationTask task = new SolutionEvaluationTask(userId, problemId, Category.ARRAY, "풀이", NOW);
        task.startProcessing(NOW);
        task.markFailed("AI_SERVER_UNAVAILABLE", "ai_server_unavailable", NOW);
        return em.persist(task);
    }

    // 아직 진행 중인 평가 작업을 저장한다.
    private SolutionEvaluationTask pendingTask(Long userId, Long problemId) {
        return em.persist(new SolutionEvaluationTask(userId, problemId, Category.ARRAY, "풀이", NOW));
    }

    // 평가 작업에 연결된 알림을 저장한다.
    private Notification notificationOf(SolutionEvaluationTask task) {
        return repository.save(new Notification(task.getUserId(), NotificationType.NL_EVALUATION_DONE,
                task.getProblemId(), task.getId(), "문구", NOW));
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
    void findOfLatestTasks_returnsNewestTenOfMine_andExcludesOthers() {
        // Given: 내 문제 11개(문제마다 작업 1개와 알림 1개)와 다른 사용자 알림 1개
        List<SolutionEvaluationTask> mine = new java.util.ArrayList<>();
        for (long problemId = 1; problemId <= 11; problemId++) {
            SolutionEvaluationTask task = finishedTask(1L, problemId);
            notificationOf(task);
            mine.add(task);
        }
        notificationOf(finishedTask(2L, 1L));
        repository.flush();

        // When
        List<Notification> recent = repository.findOfLatestTasks(1L, Limit.of(10));

        // Then: 가장 오래된 1개가 빠지고 최신순
        assertThat(recent).hasSize(10);
        assertThat(recent).extracting(Notification::getUserId).containsOnly(1L);
        assertThat(recent).extracting(Notification::getEvaluationTaskId)
                .containsExactlyElementsOf(mine.stream().skip(1).map(SolutionEvaluationTask::getId)
                        .sorted(java.util.Comparator.reverseOrder()).toList());
    }

    @Test
    void findOfLatestTasks_includesReadNotifications() {
        // Given
        Notification read = notificationOf(finishedTask(1L, 1L));
        read.markRead(NOW.plusMinutes(1));
        notificationOf(finishedTask(1L, 2L));
        repository.flush();

        // When
        List<Notification> recent = repository.findOfLatestTasks(1L, Limit.of(10));

        // Then
        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(Notification::isRead).containsExactly(false, true);
    }

    @Test
    void findOfLatestTasks_excludesOlderNotification_whenSameProblemWasResubmitted() {
        // Given: 같은 문제에 작업 2개, 각각 알림이 있음
        notificationOf(finishedTask(1L, 1L));
        Notification latest = notificationOf(finishedTask(1L, 1L));
        repository.flush();

        // When
        List<Notification> recent = repository.findOfLatestTasks(1L, Limit.of(10));

        // Then: 최신 작업의 알림만 남음
        assertThat(recent).extracting(Notification::getId).containsExactly(latest.getId());
    }

    @Test
    void findOfLatestTasks_excludesOlderNotification_whenNewerTaskIsStillPending_andKeepsOtherProblems() {
        // Given: 문제 1은 알림 뒤에 새 작업이 진행 중, 문제 2는 그대로
        notificationOf(finishedTask(1L, 1L));
        pendingTask(1L, 1L);
        Notification otherProblem = notificationOf(finishedTask(1L, 2L));
        repository.flush();

        // When
        List<Notification> recent = repository.findOfLatestTasks(1L, Limit.of(10));

        // Then: 문제 1의 이전 알림은 빠지고 문제 2의 알림만 남음
        assertThat(recent).extracting(Notification::getId).containsExactly(otherProblem.getId());
    }

    @Test
    void countUnreadOfLatestTasks_countsAllUnreadOfMine_evenBeyondTenShown() {
        // Given: 안 읽은 알림 12개(문제 12개) 중 2개를 읽음 처리, 다른 사용자 알림 1개
        for (long problemId = 1; problemId <= 12; problemId++) {
            Notification notification = notificationOf(finishedTask(1L, problemId));
            if (problemId <= 2) {
                notification.markRead(NOW.plusMinutes(1));
            }
        }
        notificationOf(finishedTask(2L, 1L));
        repository.flush();

        // When & Then
        assertThat(repository.countUnreadOfLatestTasks(1L)).isEqualTo(10);
        assertThat(repository.countUnreadOfLatestTasks(3L)).isZero();
    }

    @Test
    void countUnreadOfLatestTasks_excludesNotificationOfReplacedTask() {
        // Given: 문제 1은 이전 작업의 안 읽은 알림이 있고 새 작업이 진행 중, 문제 2는 안 읽은 알림 1개
        notificationOf(finishedTask(1L, 1L));
        pendingTask(1L, 1L);
        notificationOf(finishedTask(1L, 2L));
        repository.flush();

        // When & Then
        assertThat(repository.countUnreadOfLatestTasks(1L)).isEqualTo(1);
    }

    @Test
    void findByEvaluationTaskId_returnsNotificationOfThatTask() {
        // Given
        SolutionEvaluationTask task = finishedTask(1L, 1L);
        Notification saved = notificationOf(task);
        repository.flush();

        // When & Then
        assertThat(repository.findByEvaluationTaskId(task.getId())).get().extracting(Notification::getId)
                .isEqualTo(saved.getId());
        assertThat(repository.findByEvaluationTaskId(task.getId() + 1)).isEmpty();
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
