package com.cosmos.cosmos_backend.battlealarm.domain;

/** 배틀 알림 모달의 표시 상태. 서버 시각과 배틀 준비 여부로 정함. */
public enum BattleAlarmStatus {
    // 12:00 전
    NOT_OPEN,
    // 12:00 이상 12:10 미만, 오늘 배틀과 케이스가 준비됨
    OPEN,
    // 12:00 이상 12:10 미만인데 오늘 배틀이나 케이스가 준비되지 않음
    NO_BATTLE,
    // 12:10 이후
    CLOSED
}
