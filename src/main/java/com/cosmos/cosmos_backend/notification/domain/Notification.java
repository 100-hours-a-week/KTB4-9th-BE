package com.cosmos.cosmos_backend.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 평가 완료·실패 알림. 평가 작업 하나당 알림 하나. */
@Entity
@Table(
        name = "notifications",
        // 같은 평가 작업에 알림이 두 번 생기지 않도록 막음
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_evaluation_task",
                columnNames = "evaluation_task_id"),
        // 사용자별 안 읽은 알림 개수·최신순 조회용 인덱스
        indexes = @Index(
                name = "idx_notifications_user_read",
                columnList = "user_id, read_at, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "evaluation_task_id", nullable = false)
    private Long evaluationTaskId;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    // 읽은 시각 (NULL이면 안 읽은 알림)
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 알림 생성. 읽은 시각은 비어 있는 안 읽음 상태로 시작. */
    public Notification(Long userId, NotificationType type, Long problemId, Long evaluationTaskId,
                        String message, LocalDateTime now) {
        this.userId = userId;
        this.type = type;
        this.problemId = problemId;
        this.evaluationTaskId = evaluationTaskId;
        this.message = message;
        this.createdAt = now;
    }

    /** 읽음 처리. 이미 읽었으면 처음 읽은 시각을 그대로 둠. */
    public void markRead(LocalDateTime now) {
        // 1. 이미 읽은 알림이면 아무것도 하지 않음
        if (readAt != null) {
            return;
        }
        // 2. 읽은 시각을 기록
        this.readAt = now;
    }

    /** 읽었는지 여부. */
    public boolean isRead() {
        return readAt != null;
    }
}
