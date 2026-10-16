package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleSubmissionRepository extends JpaRepository<BattleSubmission,Long> {
}
