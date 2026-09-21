package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BattleParticipationRepository extends JpaRepository<BattleParticipation,Long> {
}
