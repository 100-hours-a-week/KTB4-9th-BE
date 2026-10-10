package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DailyBattleRepository extends JpaRepository<DailyBattle, Long> {
}
