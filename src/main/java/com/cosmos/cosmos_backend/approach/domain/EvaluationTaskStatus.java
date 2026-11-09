package com.cosmos.cosmos_backend.approach.domain;

/** 자연어 풀이 평가 작업의 처리 상태. */
public enum EvaluationTaskStatus {
    PENDING,      // 접수됨, 워커가 아직 안 가져감
    PROCESSING,   // 워커가 가져가서 AI 평가 중
    RETRY_WAIT,   // 일시 오류로 다음 재시도를 기다리는 중
    SUCCEEDED,    // AI 평가와 결과 저장까지 성공 (최종)
    FAILED        // 재시도 끝에 최종 실패 (최종)
}
