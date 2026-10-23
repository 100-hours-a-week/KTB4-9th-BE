package com.cosmos.cosmos_backend.battlealarm.domain;

import com.cosmos.cosmos_backend.global.config.BattleSchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 배틀 알림 모달의 표시 시간 기준. 설정된 배틀 시작 이상 결과 공개 미만에만 열림. */
@Component
@RequiredArgsConstructor
public final class BattleAlarmTimePolicy {

    private final BattleSchedule battleSchedule;

    /** 설정된 배틀 시작 시각 전인지 확인. */
    public boolean isBeforeOpen(LocalTime nowKst) {
        return nowKst.isBefore(battleSchedule.openTime());
    }

    /** 설정된 결과 공개 시각부터 닫혔는지 확인. */
    public boolean isClosed(LocalTime nowKst) {
        return !nowKst.isBefore(battleSchedule.resultTime());
    }

    /** 해당 날짜의 결과 공개 시각을 오프셋이 붙은 시각으로 반환. */
    public OffsetDateTime closesAt(LocalDate date, ZoneId zone) {
        return date.atTime(battleSchedule.resultTime()).atZone(zone).toOffsetDateTime();
    }
}
