package com.cosmos.cosmos_backend.approach.domain;

/** 제출 당시 AI가 판정한 핵심 키워드 하나의 포함 여부. keyword_results JSON에 저장됨. */
public record KeywordResult(String keyword, boolean isIncluded) {
}
