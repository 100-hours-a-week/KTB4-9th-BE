package com.cosmos.cosmos_backend.savedproblem.domain;

/** 저장한 문제의 제출 상태. 자연어 풀이의 is_solved로 정해짐. */
public enum SavedProblemStatus {
    NOT_SUBMITTED,
    IN_PROGRESS,
    CORRECT;

    // is_solved가 null이면 제출 기록이 없는 것
    public static SavedProblemStatus from(Boolean isSolved) {
        // 1. 제출 기록이 없으면 미제출
        if (isSolved == null) {
            return NOT_SUBMITTED;
        }

        // 2. 정답을 맞힌 적이 있으면 정답, 아니면 풀이 중
        return isSolved ? CORRECT : IN_PROGRESS;
    }
}
