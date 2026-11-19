package com.cosmos.cosmos_backend.dailyBattle.projection;

import java.time.LocalDate;

public interface BattleBasicInfoProjection {

    Long getBattleId();

    LocalDate getBattleDate();
}
