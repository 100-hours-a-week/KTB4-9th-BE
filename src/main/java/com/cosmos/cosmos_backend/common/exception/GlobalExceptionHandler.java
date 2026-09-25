package com.cosmos.cosmos_backend.common.exception;

import com.cosmos.cosmos_backend.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 컨트롤러·서비스에서 던져진 예외를 받아 message + data 형식의 응답으로 변환 (스프링이 자동으로 호출). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 우리가 던진 BusinessException 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        // 1. 예외가 들고 있는 상태코드를 응답 상태로 사용
        // 2. 예외의 message(코드 이름)와 data를 message + data 형식으로 감싸서 반환
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.of(e.getMessage(), e.getData()));
    }

    // 요청 값 검증(@Valid) 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        // 1. 검증에 실패한 첫 번째 필드의 메시지(코드 이름)를 꺼냄 (없으면 invalid_request)
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("invalid_request");
        // 2. 400과 함께 message + data 형식으로 반환
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.of(message, null));
    }

    // 위에서 처리하지 못한 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        // 1. 예상하지 못한 오류이므로 서버 로그에 원인을 남김
        log.error("Unhandled exception", e);
        // 2. 응답에는 500과 internal_server_error만 담아 반환
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.of("internal_server_error", null));
    }
}
