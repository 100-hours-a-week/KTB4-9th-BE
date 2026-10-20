package com.cosmos.cosmos_backend.approach.client;

import com.cosmos.cosmos_backend.approach.domain.EvaluationFailureCode;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** AI 평가 호출 실패. 재시도할 만한 오류인지와 저장할 실패 코드를 함께 담음. */
@Getter
public class AiEvaluationFailedException extends BusinessException {

    private final EvaluationFailureCode failureCode;
    // 다시 시도하면 성공할 수 있는 오류인지
    private final boolean retryable;
    // AI 서버가 준 errorCode (예: INVALID_CATEGORY), 없으면 null
    private final String detail;

    public AiEvaluationFailedException(HttpStatus status, String message, EvaluationFailureCode failureCode, boolean retryable, String detail) {
        super(status, message);
        this.failureCode = failureCode;
        this.retryable = retryable;
        this.detail = detail;
    }
}
