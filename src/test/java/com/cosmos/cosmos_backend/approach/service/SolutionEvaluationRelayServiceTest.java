package com.cosmos.cosmos_backend.approach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SolutionEvaluationRelayServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 30, 0);

    @Mock
    private SolutionEvaluationTaskRepository taskRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private NotificationService notificationService;

    private SolutionEvaluationRelayService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-19T03:30:00Z"), ZoneId.of("Asia/Seoul"));
        EvaluationMqProperties properties = new EvaluationMqProperties(
                true, "evaluation.request", "evaluation.result", "evaluation.request.dlq", Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofMinutes(2), Duration.ofSeconds(1), 50, Duration.ofSeconds(5));
        service = new SolutionEvaluationRelayService(taskRepository, problemRepository, notificationService, properties, fixedClock);
    }

    // 접수 시각을 지정한 PENDING 작업을 만들고 id를 채움
    private SolutionEvaluationTask pendingTask(Long id, LocalDateTime createdAt) {
        SolutionEvaluationTask task = new SolutionEvaluationTask(10L, 1L, Category.ARRAY, "풀이", createdAt);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private Problem problem() {
        return new Problem(Difficulty.LV1, Category.ARRAY, "제목", "내용", "입력 형식", "출력 형식",
                List.<AiProblemsCreateRequestDto.InputConstraints>of(), "선정 배경");
    }

    @Test
    void claimForPublish_reservesTasksForPublishLease_andReturnsIds() {
        // Given
        SolutionEvaluationTask first = pendingTask(1L, NOW.minusSeconds(3));
        SolutionEvaluationTask second = pendingTask(2L, NOW.minusSeconds(2));
        when(taskRepository.findPublishTargetsForUpdate(NOW, 50)).thenReturn(List.of(first, second));

        // When
        List<Long> ids = service.claimForPublish(50);

        // Then
        assertThat(ids).containsExactly(1L, 2L);
        assertThat(first.getNextRetryAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(second.getNextRetryAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(first.getStatus()).isEqualTo(EvaluationTaskStatus.PENDING);
    }

    @Test
    void claimForPublish_returnsEmpty_whenNothingToPublish() {
        // Given
        when(taskRepository.findPublishTargetsForUpdate(NOW, 50)).thenReturn(List.of());

        // When & Then
        assertThat(service.claimForPublish(50)).isEmpty();
    }

    @Test
    void markPublished_movesPendingTaskToProcessing() {
        // Given
        SolutionEvaluationTask task = pendingTask(1L, NOW.minusSeconds(3));
        when(taskRepository.findForUpdateById(1L)).thenReturn(Optional.of(task));

        // When
        boolean changed = service.markPublished(1L);

        // Then
        assertThat(changed).isTrue();
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PROCESSING);
        assertThat(task.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void markPublished_leavesTaskAlone_whenResultArrivedFirst() {
        // Given
        SolutionEvaluationTask task = pendingTask(1L, NOW.minusSeconds(3));
        task.markSucceeded(501L, NOW.minusSeconds(1));
        when(taskRepository.findForUpdateById(1L)).thenReturn(Optional.of(task));

        // When
        boolean changed = service.markPublished(1L);

        // Then
        assertThat(changed).isFalse();
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.SUCCEEDED);
    }

    @Test
    void failExpiredPending_failsTaskAndCreatesNotification_whenOverLimit() {
        // Given
        SolutionEvaluationTask task = pendingTask(1L, NOW.minusMinutes(3));
        when(taskRepository.findForUpdateById(1L)).thenReturn(Optional.of(task));
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem()));

        // When
        service.failExpiredPending(1L);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.FAILED);
        assertThat(task.getErrorCode()).isEqualTo("AI_SERVER_UNAVAILABLE");
        assertThat(task.getErrorMessage()).isEqualTo("publish_failed");
        verify(notificationService).createEvaluationNotification(
                eq(10L), eq(1L), eq(1L), eq(NotificationType.NL_EVALUATION_FAILED), eq("제목"));
    }

    @Test
    void failExpiredPending_doesNothing_whenWithinLimit() {
        // Given
        SolutionEvaluationTask task = pendingTask(1L, NOW.minusSeconds(30));
        when(taskRepository.findForUpdateById(1L)).thenReturn(Optional.of(task));

        // When
        service.failExpiredPending(1L);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PENDING);
        verify(notificationService, never()).createEvaluationNotification(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void failExpiredPending_doesNothing_whenAlreadyPublished() {
        // Given
        SolutionEvaluationTask task = pendingTask(1L, NOW.minusMinutes(3));
        task.markPublished(NOW.minusSeconds(1));
        when(taskRepository.findForUpdateById(1L)).thenReturn(Optional.of(task));

        // When
        service.failExpiredPending(1L);

        // Then
        assertThat(task.getStatus()).isEqualTo(EvaluationTaskStatus.PROCESSING);
        verify(notificationService, never()).createEvaluationNotification(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void findExpiredPendingIds_usesPendingFailAfterAsThreshold() {
        // Given
        when(taskRepository.findExpiredPendingIds(NOW.minusMinutes(2))).thenReturn(List.of(7L));

        // When & Then
        assertThat(service.findExpiredPendingIds()).containsExactly(7L);
    }
}
