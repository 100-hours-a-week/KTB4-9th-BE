package com.cosmos.cosmos_backend.approach.dto.response;

/** 사용자가 제출한 문제의 정답 카테고리와 선정 배경. */
public record CategoryAnswerResponse(Long problemId, String correctCategory, String categorySelectReason) {
}
