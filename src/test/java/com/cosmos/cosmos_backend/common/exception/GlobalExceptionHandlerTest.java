package com.cosmos.cosmos_backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.common.response.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessException_returnsGivenStatusAndMessageWithNullData() {
        BusinessException exception = new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found");

        ResponseEntity<ApiResponse<Object>> response = handler.handleBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("problem_not_found");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void handleBusinessException_includesDataWhenProvided() {
        Map<String, Object> data = Map.of("limit", 3, "usedCount", 3);
        BusinessException exception = new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "daily_problem_limit_exceeded", data);

        ResponseEntity<ApiResponse<Object>> response = handler.handleBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().data()).isEqualTo(data);
    }

    @Test
    void handleValidationException_returns400WithFirstFieldErrorMessage() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "field", "invalid_field"));
        MethodParameter methodParameter = new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Object>> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("invalid_field");
    }

    @Test
    void handleException_returns500WithInternalServerErrorMessage() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("internal_server_error");
        assertThat(response.getBody().data()).isNull();
    }

    private void dummy(String value) {
    }
}
