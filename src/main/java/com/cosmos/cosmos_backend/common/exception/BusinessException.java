package com.cosmos.cosmos_backend.common.exception;

import org.springframework.http.HttpStatus;

/** 실패를 알릴 때 던지는 예외. 상태코드, message(코드 이름), data를 담고 GlobalExceptionHandler가 응답으로 변환. */
public class BusinessException extends RuntimeException {

    // 응답 상태코드 (예: 404)
    private final HttpStatus status;
    // 응답 data (없으면 null)
    private final Object data;

    // data 없이 생성
    public BusinessException(HttpStatus status, String message) {
        this(status, message, null);
    }

    // data를 함께 담아 생성
    public BusinessException(HttpStatus status, String message, Object data) {
        // message(코드 이름)를 예외에 저장
        super(message);
        this.status = status;
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getData() {
        return data;
    }
}
