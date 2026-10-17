package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.projection.TopRankingProjection;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    // 정답 3개를 모두 맞춘 사람 중 풀이 시간이 가장 빠른 5명 → projection으로
    @Query(value = """
    SELECT
        u.id AS userId,
        u.name AS userName,
        u.profile_image_url AS profileImageUrl,
        TIMESTAMPDIFF(
            MICROSECOND,
            bp.started_at,
            bp.submitted_at
        ) AS elapsedMicros
    FROM battle_participations bp
    JOIN users u ON bp.user_id = u.id
    WHERE bp.daily_battle_id = :battleId
      AND bp.all_correct = true
      AND bp.participation_status = 'EVALUATING_COMPLETED'
    ORDER BY elapsedMicros ASC, bp.submitted_at ASC, bp.participation_id ASC
    LIMIT 5
    """, nativeQuery = true)
    List<TopRankingProjection> findTop5ByBattleId(@Param("battleId") Long battleId);



}
