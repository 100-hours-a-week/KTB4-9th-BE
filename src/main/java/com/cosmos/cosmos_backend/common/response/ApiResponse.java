package com.cosmos.cosmos_backend.common.response;

/** 모든 API 응답의 공통 형식 { message, data }. message는 코드 이름(예: problem_not_found). */
public record ApiResponse<T>(String message, T data) {

    // message와 data를 담아 응답 형식을 생성
    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(message, data);
    }

    // data 없이 message만 담아 응답 형식을 생성
    public static ApiResponse<Void> of(String message) {
        return new ApiResponse<>(message, null);
    }
}
