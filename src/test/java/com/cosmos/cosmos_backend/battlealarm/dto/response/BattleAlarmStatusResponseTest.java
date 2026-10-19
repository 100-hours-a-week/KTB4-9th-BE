package com.cosmos.cosmos_backend.battlealarm.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarmStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BattleAlarmStatusResponseTest {

    private static final OffsetDateTime SERVER_TIME = OffsetDateTime.parse("2026-10-13T12:50:01+09:00");
    private static final OffsetDateTime CLOSES_AT = OffsetDateTime.parse("2026-10-13T13:00:00+09:00");

    @Test
    void notOpen_hasNoBattleIdAndFalseFlags() {
        // When
        BattleAlarmStatusResponse response = BattleAlarmStatusResponse.notOpen(SERVER_TIME, CLOSES_AT);

        // Then: OPEN이 아니므로 battle_id는 null, 나머지는 false
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.NOT_OPEN);
        assertThat(response.battleId()).isNull();
        assertThat(response.participated()).isFalse();
        assertThat(response.dismissed()).isFalse();
        assertThat(response.serverTime()).isEqualTo(SERVER_TIME);
        assertThat(response.closesAt()).isEqualTo(CLOSES_AT);
    }

    @Test
    void closed_keepsBattleIdForResultLookup_andFalseFlags() {
        // When
        BattleAlarmStatusResponse response = BattleAlarmStatusResponse.closed(SERVER_TIME, 3L, CLOSES_AT);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.CLOSED);
        assertThat(response.battleId()).isEqualTo(3L);
        assertThat(response.participated()).isFalse();
        assertThat(response.dismissed()).isFalse();
    }

    @Test
    void noBattle_hasNoBattleIdAndFalseFlags() {
        // When
        BattleAlarmStatusResponse response = BattleAlarmStatusResponse.noBattle(SERVER_TIME, CLOSES_AT);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.NO_BATTLE);
        assertThat(response.battleId()).isNull();
        assertThat(response.participated()).isFalse();
        assertThat(response.dismissed()).isFalse();
    }

    @Test
    void open_keepsBattleIdAndFlags() {
        // When
        BattleAlarmStatusResponse response = BattleAlarmStatusResponse.open(SERVER_TIME, 3L, true, false, CLOSES_AT);

        // Then: OPEN일 때만 배틀 id와 참여, 닫음 여부가 담김
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.OPEN);
        assertThat(response.battleId()).isEqualTo(3L);
        assertThat(response.participated()).isTrue();
        assertThat(response.dismissed()).isFalse();
    }
}
