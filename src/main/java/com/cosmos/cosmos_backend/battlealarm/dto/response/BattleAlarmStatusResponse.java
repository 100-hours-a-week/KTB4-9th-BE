package com.cosmos.cosmos_backend.battlealarm.dto.response;

import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarmStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** 배틀 알림 상태 확인 응답. OPEN이 아니면 battle_id는 null, participated와 dismissed는 false. */
public record BattleAlarmStatusResponse(
        BattleAlarmStatus status,
        @JsonProperty("server_time") OffsetDateTime serverTime,
        @JsonProperty("battle_id") Long battleId,
        boolean participated,
        boolean dismissed,
        @JsonProperty("closes_at") OffsetDateTime closesAt
) {

    /** 12:00 전 응답. */
    public static BattleAlarmStatusResponse notOpen(OffsetDateTime serverTime, OffsetDateTime closesAt) {
        return new BattleAlarmStatusResponse(BattleAlarmStatus.NOT_OPEN, serverTime, null, false, false, closesAt);
    }

    /** 12:10 이후 응답. */
    public static BattleAlarmStatusResponse closed(OffsetDateTime serverTime, OffsetDateTime closesAt) {
        return new BattleAlarmStatusResponse(BattleAlarmStatus.CLOSED, serverTime, null, false, false, closesAt);
    }

    /** 열려 있어야 하는 시간인데 배틀이 준비되지 않은 응답. */
    public static BattleAlarmStatusResponse noBattle(OffsetDateTime serverTime, OffsetDateTime closesAt) {
        return new BattleAlarmStatusResponse(BattleAlarmStatus.NO_BATTLE, serverTime, null, false, false, closesAt);
    }

    /** 열려 있는 응답. 이 경우에만 배틀 id와 참여, 닫음 여부를 담음. */
    public static BattleAlarmStatusResponse open(
            OffsetDateTime serverTime,
            Long battleId,
            boolean participated,
            boolean dismissed,
            OffsetDateTime closesAt
    ) {
        return new BattleAlarmStatusResponse(
                BattleAlarmStatus.OPEN, serverTime, battleId, participated, dismissed, closesAt
        );
    }
}
