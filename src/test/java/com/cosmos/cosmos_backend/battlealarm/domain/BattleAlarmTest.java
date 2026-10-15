package com.cosmos.cosmos_backend.battlealarm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BattleAlarmTest {

    @Test
    void create_keepsGivenValues_andIsNotDismissed() {
        // When
        BattleAlarm alarm = new BattleAlarm(7L, 3L);

        // Then: 사용자와 배틀이 그대로 들어가고, 처음에는 닫지 않은 상태
        assertThat(alarm.getUserId()).isEqualTo(7L);
        assertThat(alarm.getBattleId()).isEqualTo(3L);
        assertThat(alarm.isDismissed()).isFalse();
    }

    @Test
    void dismiss_setsDismissedTrue_whenNotDismissed() {
        // Given: 닫지 않은 상태
        BattleAlarm alarm = new BattleAlarm(7L, 3L);

        // When
        alarm.dismiss();

        // Then: 닫음 상태가 됨
        assertThat(alarm.isDismissed()).isTrue();
    }

    @Test
    void dismiss_staysDismissed_whenCalledTwice() {
        // Given: 이미 닫은 상태
        BattleAlarm alarm = new BattleAlarm(7L, 3L);
        alarm.dismiss();

        // When: 다시 닫기
        alarm.dismiss();

        // Then: 닫음 상태 그대로
        assertThat(alarm.isDismissed()).isTrue();
    }
}
