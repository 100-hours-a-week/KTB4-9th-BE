package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface BattleParticipationRepository extends JpaRepository<BattleParticipation,Long> {
    Optional<BattleParticipation> findByUser_Id(Long userId);

    Optional<BattleParticipation> findByDailyBattleIdAndUser_Id(Long battleId, Long userId);

    // 제출 처리를 위한 상태 변경 조건부 update 직접 실행
    @Modifying
    @Query("""
    UPDATE BattleParticipation bp
    SET bp.participationStatus = :submittedStatus,
        bp.submittedAt = :submittedAt
    WHERE bp.participationId = :participationId
      AND bp.participationStatus = :expectedStatus
    """)
    Integer updateStatusIfMatch(
            @Param("participationId") Long participationId,
            @Param("expectedStatus") ParticipationStatus expectedStatus,
            @Param("submittedStatus") ParticipationStatus submittedStatus,
            @Param("submittedAt") LocalDateTime submittedAt
    );

    // 채점 worker의 작업 선점을 위한 메소드(제출 처리인 상태인 행 중에 제출 시각 빠른것, 락 가진건 건너 뛰기)
    @Query(value = """
    SELECT *
    FROM battle_participations
    WHERE participation_status = 'SUBMITTED'
    ORDER BY submitted_at ASC
    LIMIT 1
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    Optional<BattleParticipation> findSubmittedForUpdate();

    // 채점 미완료된 제출있는지 확인
    Boolean existsByDailyBattleIdAndParticipationStatusIn(Long battleId, Collection<ParticipationStatus> statuses);
}
