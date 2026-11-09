package com.cosmos.cosmos_backend.dailyBattle.domain.entity;

import com.cosmos.cosmos_backend.dailyBattle.domain.BattleAnswerPayload;
import com.cosmos.cosmos_backend.dailyBattle.domain.BattleEventType;
import com.cosmos.cosmos_backend.dailyBattle.domain.BattleSubmissionEventStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_event_id")
    private Long outboxEventId;

    @Column(name = "participation_id", nullable = false, unique = true)
    private Long participationId;

    // 어떤 작업 요청인지 구분하는 타입 → 이 타입 보고 어떤 exchange로 갈지 정하기
    @Column(name = "battle_event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private BattleEventType battleEventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "battle_answer_payload", nullable = false, columnDefinition = "json")
    private BattleAnswerPayload battleAnswerPayload;

    @Column(name = "battle_submission_event_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private BattleSubmissionEventStatus battleSubmissionEventStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // 발행 재시도 횟수 (최대 3회)
    @Column(name = "published_attempt_count", nullable = false)
    private int publishedAttemptCount;

    // outbox publisher의 임대 토큰
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "publisher_lease_token", columnDefinition = "BINARY(16)")
    private UUID publisherLeaseToken;

    // outbox publisher의 임대 시간
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "publisher_lease_expires_at", columnDefinition = "TIMESTAMP(3)")
    private Instant publisherLeaseExpiresAt;


    public OutboxEvent(Long participationId, BattleEventType battleEventType, BattleAnswerPayload battleAnswerPayload) {
        this.participationId = participationId;
        this.battleEventType = battleEventType;
        this.publishedAttemptCount = 0;
        this.battleAnswerPayload = battleAnswerPayload;
        this.battleSubmissionEventStatus = BattleSubmissionEventStatus.PENDING;
    }

    public void changedPublishingStatus(UUID publisherLeaseToken, Instant publisherLeaseExpiresAt) {
        this.battleSubmissionEventStatus = BattleSubmissionEventStatus.PUBLISHING;
        this.publisherLeaseToken = publisherLeaseToken;
        this.publisherLeaseExpiresAt = publisherLeaseExpiresAt;
    }

    public void increasePublishedAttemptCount() {
        this.publishedAttemptCount++;
    }

    public void changeStatusFailed(){
        this.battleSubmissionEventStatus = BattleSubmissionEventStatus.PUBLISH_FAILED;
    }

}
