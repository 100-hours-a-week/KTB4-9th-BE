package com.cosmos.cosmos_backend.battlealarm.repository;

import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleAlarmRepository extends JpaRepository<BattleAlarm, Long> {

    // 사용자가 해당 배틀 알림을 닫았는지 확인
    boolean existsByUserIdAndBattleIdAndDismissedTrue(Long userId, Long battleId);

    // 사용자와 배틀로 알림 상태 행 조회 (닫기 저장 때 이미 있는지 확인)
    Optional<BattleAlarm> findByUserIdAndBattleId(Long userId, Long battleId);
}
