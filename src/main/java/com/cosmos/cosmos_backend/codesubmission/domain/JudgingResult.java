package com.cosmos.cosmos_backend.codesubmission.domain;

/** 코드 채점 결과. */
public enum JudgingResult {
    CORRECT,
    WRONG_ANSWER,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED
}
