package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BattleCaseRepository extends JpaRepository<BattleCase, Integer> {
    List<BattleCase> findByDailyBattle_BattleId(Long battleId);
}
