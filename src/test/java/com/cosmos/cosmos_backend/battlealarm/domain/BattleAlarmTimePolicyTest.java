package com.cosmos.cosmos_backend.battlealarm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BattleAlarmTimePolicyTest {

    @Test
    void isBeforeOpen_isTrue_justBefore1200() {
        // When & Then: 11:59:59.999는 아직 열리기 전
        assertThat(BattleAlarmTimePolicy.isBeforeOpen(LocalTime.of(11, 59, 59, 999_000_000))).isTrue();
    }

    @Test
    void isBeforeOpen_isFalse_at1200() {
        // When & Then: 12:00:00 정각부터 열림
        assertThat(BattleAlarmTimePolicy.isBeforeOpen(LocalTime.of(12, 0, 0))).isFalse();
        assertThat(BattleAlarmTimePolicy.isClosed(LocalTime.of(12, 0, 0))).isFalse();
    }

    @Test
    void isClosed_isFalse_justBefore1210() {
        // When & Then: 12:09:59.999는 아직 안 닫힘
        assertThat(BattleAlarmTimePolicy.isClosed(LocalTime.of(12, 9, 59, 999_000_000))).isFalse();
    }

    @Test
    void isClosed_isTrue_at1210() {
        // When & Then: 12:10:00 정각부터 닫힘
        assertThat(BattleAlarmTimePolicy.isClosed(LocalTime.of(12, 10, 0))).isTrue();
    }

    @Test
    void closesAt_returns1210WithSeoulOffset() {
        // When
        OffsetDateTime closesAt = BattleAlarmTimePolicy.closesAt(LocalDate.of(2026, 10, 13), ZoneId.of("Asia/Seoul"));

        // Then: 그날 12:10 (+09:00)
        assertThat(closesAt).isEqualTo(OffsetDateTime.parse("2026-10-13T12:10:00+09:00"));
    }
}
