package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public interface DailyBattleRepository extends JpaRepository<DailyBattle, Long> {

    // 날짜로 그날의 배틀 조회
    Optional<DailyBattle> findByBattleDate(LocalDate battleDate);

    // 날짜로 그날의 배틀 id 조회
    @Query("""
        SELECT d.battleId
        FROM DailyBattle d
        WHERE d.battleDate = :battleDate
        """)
    Optional<Long> findIdByBattleDate(@Param("battleDate") LocalDate battleDate);

    // 배틀 결과 나왔는지
    boolean existsByBattleDateAndResultPublishedAtIsNull(LocalDate battleDate);

    // 배틀 결과 공개 안 된 것만 공개 처리
    @Modifying
    @Query("""
        UPDATE DailyBattle d
        SET d.resultPublishedAt = :publishedAt
        WHERE d.id = :battleId
          AND d.resultPublishedAt IS NULL
    """)
    int publishResultIfNotPublished(Long battleId, LocalDateTime publishedAt);

}
