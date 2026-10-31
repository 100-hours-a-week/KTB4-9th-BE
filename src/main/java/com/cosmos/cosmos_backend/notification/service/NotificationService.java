package com.cosmos.cosmos_backend.notification.service;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.notification.domain.Notification;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import com.cosmos.cosmos_backend.notification.dto.response.NotificationListResponse;
import com.cosmos.cosmos_backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평가 알림 생성·조회·읽음 처리. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String DONE_MESSAGE = "'%s' 평가가 완료됐어요.";
    private static final String FAILED_MESSAGE = "'%s' 평가를 완료하지 못했어요. 다시 제출해 주세요.";

    private final NotificationRepository notificationRepository;
    private final SolutionEvaluationTaskRepository evaluationTaskRepository;
    private final Clock clock;

    /** 평가 작업이 최종 상태가 될 때 알림을 생성. 호출한 쪽 트랜잭션에 합류. */
    @Transactional
    public void createEvaluationNotification(Long userId, Long problemId, Long evaluationTaskId,
                                             NotificationType type, String problemTitle) {
        // 1. 알림 종류에 맞는 문구를 만듦
        String message = switch (type) {
            case NL_EVALUATION_DONE -> String.format(DONE_MESSAGE, problemTitle);
            case NL_EVALUATION_FAILED -> String.format(FAILED_MESSAGE, problemTitle);
        };

        // 2. 알림을 저장 (읽은 시각은 비어 있음)
        notificationRepository.save(new Notification(userId, type, problemId, evaluationTaskId, message, now()));
    }

    /** 문제별 최신 평가의 알림(읽음 포함), 같은 조건의 안 읽은 전체 개수, 진행 중인 평가 여부를 조회. */
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId) {
        // 1. 문제별 최신 작업의 알림을 최신순 10개 조회
        List<Notification> recent = notificationRepository.findOfLatestTasks(userId, Limit.of(10));

        // 2. 같은 조건의 안 읽은 알림 전체 개수를 셈
        long unreadCount = notificationRepository.countUnreadOfLatestTasks(userId);

        // 3. 진행 중인 평가 작업이 있는지 확인
        boolean hasPendingEvaluation = evaluationTaskRepository.existsByUserIdAndActiveSlot(
                userId, SolutionEvaluationTask.ACTIVE_SLOT);

        // 4. 응답으로 조립
        return NotificationListResponse.of(unreadCount, hasPendingEvaluation, recent);
    }

    /** 평가 작업에 연결된 알림 id. 아직 알림이 없으면 빈 값. */
    @Transactional(readOnly = true)
    public Optional<Long> findIdByEvaluationTaskId(Long evaluationTaskId) {
        return notificationRepository.findByEvaluationTaskId(evaluationTaskId).map(Notification::getId);
    }

    /** 알림을 읽음 처리. 이미 읽었어도 성공. */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        // 1. 내 알림만 조회 (없거나 남의 알림이면 404)
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "notification_not_found"));

        // 2. 읽음 처리 (이미 읽었으면 엔티티가 그대로 둠, 트랜잭션이 끝날 때 저장됨)
        notification.markRead(now());
    }

    // 현재 시각 (UTC)
    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
