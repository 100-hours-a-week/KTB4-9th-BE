package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.BattleSubmissionEventStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 아직 발행하지 않은 PENDING 이벤트를 생성 순서대로 최대 100개 조회
    // 다른 Publisher가 잠근 이벤트는 건너뛰기
    @Query(value = """
        SELECT *
        FROM outbox_events
        WHERE battle_event_type = 'GRADING_REQUEST'
          AND battle_submission_event_status = 'PENDING'
        ORDER BY created_at ASC, outbox_event_id ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findPendingForUpdate(@Param("limit") int limit);

    // Publisher 장애로 임대가 만료된 PUBLISHING 이벤트를 만료 순서대로 최대 100개 조회
    // 다른 Publisher가 잠근 이벤트는 건너뛰기
    @Query(value = """
        SELECT *
        FROM outbox_events
        WHERE battle_event_type = 'GRADING_REQUEST'
          AND battle_submission_event_status = 'PUBLISHING'
          AND publisher_lease_expires_at <= UTC_TIMESTAMP(3)
        ORDER BY publisher_lease_expires_at ASC, created_at ASC, outbox_event_id ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findExpiredPublishingForUpdate(@Param("limit") int limit);

    // 발행 가능한 이벤트를 한 번에 PUBLISHING 상태로 변경
    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE OutboxEvent o
    SET o.battleSubmissionEventStatus = :publishing,
        o.publisherLeaseToken = :leaseToken,
        o.publisherLeaseExpiresAt = :leaseExpiresAt,
        o.publishedAttemptCount = o.publishedAttemptCount + 1
    WHERE o.outboxEventId IN :eventIds
    """)
    int markAsPublishing(
            @Param("eventIds") List<Long> eventIds,
            @Param("publishing") BattleSubmissionEventStatus publishing,
            @Param("leaseToken") UUID leaseToken,
            @Param("leaseExpiresAt") Instant leaseExpiresAt
    );

    // 재시도 횟수를 초과한 이벤트를 한 번에 발행 실패 상태로 변경
    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE OutboxEvent o
    SET o.battleSubmissionEventStatus = :publishFailed
    WHERE o.outboxEventId IN :eventIds
    """)
    int markAsPublishFailed(
            @Param("eventIds") List<Long> eventIds,
            @Param("publishFailed") BattleSubmissionEventStatus publishFailed
    );


    // 이벤트 발행 완료 상태로 변경
    @Modifying
    @Query("""
    UPDATE OutboxEvent o
    SET o.battleSubmissionEventStatus = :sent,
        o.publishedAt = :publishedAt
    WHERE o.outboxEventId = :eventId
      AND o.publisherLeaseToken = :leaseToken
      AND o.battleSubmissionEventStatus = :publishing
    """)
    int markAsSent(
            Long eventId,
            UUID leaseToken,
            BattleSubmissionEventStatus sent,
            BattleSubmissionEventStatus publishing,
            LocalDateTime publishedAt
    );
}
