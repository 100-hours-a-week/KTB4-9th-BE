package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
}
