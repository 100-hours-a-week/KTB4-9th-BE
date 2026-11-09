package com.cosmos.cosmos_backend.approach.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** AI 서버 오류 응답 본문 (필요한 errorCode만 읽음). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiErrorResponse(String errorCode) {
}
