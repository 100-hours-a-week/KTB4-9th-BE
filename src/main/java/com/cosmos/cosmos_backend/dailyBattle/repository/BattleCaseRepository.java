package com.cosmos.cosmos_backend.dailyBattle.repository;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BattleCaseRepository extends JpaRepository<BattleCase, Integer> {
    List<BattleCase> findByDailyBattle_BattleIdOrderByDisplayOrderAsc(Long battleId);

    @Query("""
    SELECT bc
    FROM BattleCase bc
    WHERE bc.caseId IN :caseIds
    """)
    List<BattleCase> findAllByCaseIds(@Param("caseIds") List<Long> caseIds);
}
