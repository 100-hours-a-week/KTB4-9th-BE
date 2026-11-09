package com.cosmos.cosmos_backend.dailyBattle.domain;

public enum ParticipationStatus {
    // 참여 중, 제출 완료, 채점 중, 채점 완료, 중도 포기, 타임 오버, 채점 실패
    IN_PROGRESS,
    SUBMITTED,
    GRADING,
    GRADING_COMPLETED,
    DROPPED_OUT,
    TIME_OVER,
    GRADING_FAILED
}
