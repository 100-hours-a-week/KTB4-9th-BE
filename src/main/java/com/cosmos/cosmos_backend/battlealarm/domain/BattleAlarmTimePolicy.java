package com.cosmos.cosmos_backend.battlealarm.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 배틀 알림 모달의 표시 시간 기준. 한국 시각 12:50 이상 13:00 미만에만 열림. */
public final class BattleAlarmTimePolicy {

    private static final LocalTime OPEN_TIME = LocalTime.of(12, 50);
    private static final LocalTime CLOSE_TIME = LocalTime.of(13, 00);

    private BattleAlarmTimePolicy() {
    }

    /** 열리기 전인지 확인. 12:00 정각부터는 열림. */
    public static boolean isBeforeOpen(LocalTime nowKst) {
        return nowKst.isBefore(OPEN_TIME);
    }

    /** 닫혔는지 확인. 12:10 정각부터는 닫힘. */
    public static boolean isClosed(LocalTime nowKst) {
        return !nowKst.isBefore(CLOSE_TIME);
    }

    /** 해당 날짜의 마감 시각(12:10)을 오프셋이 붙은 시각으로 반환. */
    public static OffsetDateTime closesAt(LocalDate date, ZoneId zone) {
        return date.atTime(CLOSE_TIME).atZone(zone).toOffsetDateTime();
    }
}
