package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleCaseRepository extends JpaRepository<BattleCase, Integer> {
}
