package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.BattleSubmissionEventStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 배치 폴링 500ms 마다 최대 100개 가져오기
    // 발행 대기 또는 Publisher Lease가 만료된 이벤트를
    // 생성 순서대로 최대 100개 조회하고, 잠긴 행은 건너뛰기
    @Query(value = """
        SELECT *
        FROM outbox_events
        WHERE battle_event_type = 'GRADING_REQUEST'
        AND ( 
            battle_submission_event_status = 'PENDING'
           OR (
               battle_submission_event_status = 'PUBLISHING'
               AND publisher_lease_expires_at <= UTC_TIMESTAMP(3)
           )
        )
        ORDER BY created_at ASC, outbox_event_id ASC
        LIMIT 100
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findPublishableForUpdate();


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
