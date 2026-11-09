package com.cosmos.cosmos_backend.battlealarm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.dailyBattle.scheduler.BattleSchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BattleAlarmTimePolicyTest {

    private final BattleAlarmTimePolicy policy = new BattleAlarmTimePolicy(
            new BattleSchedule(
                    LocalTime.of(12, 50),
                    LocalTime.of(12, 59, 59),
                    LocalTime.of(13, 0)
            )
    );

    @Test
    void isBeforeOpen_isTrue_justBefore1250() {
        // When & Then: 12:49:59.999는 아직 열리기 전
        assertThat(policy.isBeforeOpen(LocalTime.of(12, 49, 59, 999_000_000))).isTrue();
    }

    @Test
    void isBeforeOpen_isFalse_at1250() {
        // When & Then: 12:50:00 정각부터 열림
        assertThat(policy.isBeforeOpen(LocalTime.of(12, 50, 0))).isFalse();
        assertThat(policy.isClosed(LocalTime.of(12, 50, 0))).isFalse();
    }

    @Test
    void isClosed_isFalse_justBefore1300() {
        // When & Then: 12:59:59.999는 아직 안 닫힘
        assertThat(policy.isClosed(LocalTime.of(12, 59, 59, 999_000_000))).isFalse();
    }

    @Test
    void isClosed_isTrue_at1300() {
        // When & Then: 13:00:00 정각부터 닫힘
        assertThat(policy.isClosed(LocalTime.of(13, 0, 0))).isTrue();
    }

    @Test
    void closesAt_returns1300WithSeoulOffset() {
        // When
        OffsetDateTime closesAt = policy.closesAt(LocalDate.of(2026, 10, 13), ZoneId.of("Asia/Seoul"));

        // Then: 그날 13:00 (+09:00)
        assertThat(closesAt).isEqualTo(OffsetDateTime.parse("2026-10-13T13:00:00+09:00"));
    }
}
