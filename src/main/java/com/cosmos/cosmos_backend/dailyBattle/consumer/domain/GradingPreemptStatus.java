package com.cosmos.cosmos_backend.dailyBattle.consumer.domain;

public enum GradingPreemptStatus {
    // 선점 완료
    PREEMPTED,

    // 다른 워커가 선점 중 + 임대 유효
    LEASE_ACTIVE,

    // 재선점 횟수 초과
    OVER_LEASE_COUNT,

    // 이미 성공
    ALREADY_COMPLETED,

    // 이미 실패
    ALREADY_FAILED,

    // 유효하지 않은 상태
    INVALID_STATUS,
}
