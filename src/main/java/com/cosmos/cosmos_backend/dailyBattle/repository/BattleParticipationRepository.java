package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.metrics.BattleStatusCountProjection;
import com.cosmos.cosmos_backend.dailyBattle.projection.MyBattleRankingProjection;
import com.cosmos.cosmos_backend.dailyBattle.projection.TopRankingProjection;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BattleParticipationRepository extends JpaRepository<BattleParticipation,Long> {

    Optional<BattleParticipation> findByDailyBattleIdAndUser_Id(Long battleId, Long userId);

    // 제출 처리를 위한 상태 변경 조건부 update 직접 실행
    // 마감 전에 접수된 요청이면 IN_PROGRESS 또는 TIME_OVER에서 SUBMITTED로 변경
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE BattleParticipation bp
           SET bp.participationStatus = :submittedStatus,
               bp.submittedAt = :submittedAt
         WHERE bp.participationId = :participationId
           AND bp.participationStatus IN (:inProgressStatus, :timeOverStatus)
           AND :submittedAt < :deadline
        """)
    int submitIfReceivedBeforeDeadline(
            @Param("participationId") Long participationId,
            @Param("inProgressStatus") ParticipationStatus inProgressStatus,
            @Param("timeOverStatus") ParticipationStatus timeOverStatus,
            @Param("submittedStatus") ParticipationStatus submittedStatus,
            @Param("submittedAt") Instant submittedAt,
            @Param("deadline") Instant deadline
    );

    // 조회 후 Dirty Checking 대신 IN_PROGRESS 상태만 직접 타임아웃 처리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE BattleParticipation bp
           SET bp.participationStatus = :timeOverStatus
         WHERE bp.dailyBattleId = :battleId
           AND bp.participationStatus = :inProgressStatus
        """)
    int timeoutIfInProgress(
            @Param("battleId") Long battleId,
            @Param("inProgressStatus") ParticipationStatus inProgressStatus,
            @Param("timeOverStatus") ParticipationStatus timeOverStatus
    );

    // 채점 worker의 작업 선점을 위한 메소드(제출 처리인 상태인 행 중에 제출 시각 빠른것, 락 가진건 건너 뛰기)
    @Query(value = """
    SELECT *
    FROM battle_participations
    WHERE participation_status = 'SUBMITTED'
       OR (
           participation_status = 'GRADING'
           AND lease_expires_at <= UTC_TIMESTAMP(3)
       )
    ORDER BY submitted_at ASC
    LIMIT 1
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    Optional<BattleParticipation> findSubmittedForUpdate();

    // 부하테스트를 위한 메트릭
    @Query("""
        SELECT bp.participationStatus AS status, COUNT(bp) AS taskCount
          FROM BattleParticipation bp
         WHERE bp.dailyBattleId = :battleId
         GROUP BY bp.participationStatus
        """)
    List<BattleStatusCountProjection> countTasksByStatus(@Param("battleId") Long battleId);

    // 채점 저장을 위한 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT bp
    FROM BattleParticipation bp
    WHERE bp.participationId = :participationId
    """)
    Optional<BattleParticipation> findByIdForUpdate(
            @Param("participationId") Long participationId
    );

    // 채점 미완료된 제출있는지 확인
    Boolean existsByDailyBattleIdAndParticipationStatusIn(Long battleId, Collection<ParticipationStatus> statuses);

    // 정답 3개를 모두 맞춘 사람 중 풀이 시간이 가장 빠른 5명 (동점자는 전부 보여주기) → projection으로
    @Query(value = """
    SELECT
        ranked.ranking AS `rank`,
        ranked.userId AS userId,
        ranked.userName AS userName,
        ranked.profileImageUrl AS profileImageUrl,
        ranked.elapsedMicros AS elapsedMicros
    FROM (
        SELECT
            bp.user_id AS userId,
            u.username AS userName,
            u.profile_image_url AS profileImageUrl,

            TIMESTAMPDIFF(
                MICROSECOND,
                bp.started_at,
                bp.submitted_at
            ) AS elapsedMicros,

            RANK() OVER (
                ORDER BY TIMESTAMPDIFF(
                    MICROSECOND,
                    bp.started_at,
                    bp.submitted_at
                )
            ) AS ranking

        FROM battle_participations bp
        JOIN users u ON bp.user_id = u.id

        WHERE bp.daily_battle_id = :battleId
          AND bp.all_correct = true
          AND bp.participation_status = 'GRADING_COMPLETED'
    ) ranked

    WHERE ranked.ranking <= 5
    ORDER BY ranked.ranking ASC, ranked.userId ASC
    """, nativeQuery = true)
    List<TopRankingProjection> findTop5Rankings(@Param("battleId") Long battleId);

    // 유저 개인 배틀 결과 (랭킹, 소요시간)
    @Query(value = """
    SELECT
        ranked.ranking AS `rank`,
        ranked.elapsedMicros AS elapsedMicros
    FROM (
        SELECT
            bp.user_id AS userId,

            TIMESTAMPDIFF(
                MICROSECOND,
                bp.started_at,
                bp.submitted_at
            ) AS elapsedMicros,

            RANK() OVER (
                ORDER BY TIMESTAMPDIFF(
                    MICROSECOND,
                    bp.started_at,
                    bp.submitted_at
                )
            ) AS ranking

        FROM battle_participations bp
        WHERE bp.daily_battle_id = :battleId
          AND bp.all_correct = true
          AND bp.participation_status = 'GRADING_COMPLETED'
    ) ranked

    WHERE ranked.userId = :userId
    """, nativeQuery = true)
    Optional<MyBattleRankingProjection> findMyBattleRanking(
            @Param("battleId") Long battleId,
            @Param("userId") Long userId
    );

    // 배틀 참여상태로 조회
    List<BattleParticipation> findAllByDailyBattleIdAndParticipationStatus(Long battleId, ParticipationStatus status);


}
