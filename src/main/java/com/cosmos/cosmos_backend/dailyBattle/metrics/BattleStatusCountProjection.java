package com.cosmos.cosmos_backend.dailyBattle.metrics;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;

// 부하테스트를 위한 메트릭
public interface BattleStatusCountProjection {

    ParticipationStatus getStatus();

    long getTaskCount();
}
