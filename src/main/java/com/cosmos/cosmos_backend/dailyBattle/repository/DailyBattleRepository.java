package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyBattleRepository extends JpaRepository<DailyBattle, Long> {

    // 날짜로 그날의 배틀 조회 (battle_date가 하루에 하나라서 결과는 최대 1건)
    Optional<DailyBattle> findByBattleDate(LocalDate battleDate);
}
