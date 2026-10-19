package com.cosmos.cosmos_backend.savedproblem.dto.request;

/** 문제 저장 요청. categoryKnown은 저장 당시 카테고리를 알고 있었는지 (없으면 false). */
public record SaveProblemRequest(Boolean categoryKnown) {
}
