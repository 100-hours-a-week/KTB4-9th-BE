package com.cosmos.cosmos_backend.approach.domain;

/** 평가 작업이 실패한 이유. error_code 컬럼에 이름 그대로 저장됨. */
public enum EvaluationFailureCode {
    AI_SERVER_TIMEOUT,      // AI 응답 시간 초과
    AI_SERVER_UNAVAILABLE,  // AI 연결 실패, 5xx, 응답 형식 이상
    AI_REQUEST_REJECTED,    // AI가 요청을 거절 (400, 422): 재시도해도 같은 결과
    WORKER_TIMEOUT,         // 워커가 오래 멈춰서 복구 스케줄러가 회수
    INTERNAL_ERROR          // 그 외 우리 쪽 오류
}
