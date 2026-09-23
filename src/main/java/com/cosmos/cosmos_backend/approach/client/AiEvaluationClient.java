package com.cosmos.cosmos_backend.approach.client;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** AI 서버에 자연어 풀이 평가를 요청하는 클라이언트. */
@Slf4j
@Component
public class AiEvaluationClient {

    private static final String EVALUATION_PATH = "/api/llm/evaluation";
    private static final int MAX_LOG_BODY_LENGTH = 300;

    private final RestClient aiRestClient;

    public AiEvaluationClient(@Qualifier("aiRestClient") RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    /** 평가를 요청하고 검증된 결과를 반환. 실패하면 503(장애) 또는 504(시간 초과). */
    public AiEvaluationResult evaluate(AiEvaluationRequest request) {
        AiEvaluationResponse response;
        try {
            // 1. AI 서버에 평가를 요청하고 응답을 받음
            response = aiRestClient.post()
                    .uri(EVALUATION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiEvaluationResponse.class);
        } catch (ResourceAccessException e) {
            // 2. 연결 실패 또는 시간 초과 (시간 초과는 504, 그 외는 503 예외를 던짐)
            if (isTimeout(e)) {
                log.warn("AI 서버 응답 시간 초과", e);
                throw timeout();
            }
            log.error("AI 서버 연결 실패", e);
            throw unavailable();
        } catch (RestClientResponseException e) {
            // 3. AI 서버가 오류 응답을 준 경우 (503 예외를 던짐)
            log.error("AI 서버 오류 응답: status={}, body={}", e.getStatusCode(), truncate(e.getResponseBodyAsString()));
            throw unavailable();
        } catch (RestClientException e) {
            // 4. 응답을 읽지 못한 경우 (503 예외를 던짐)
            log.error("AI 서버 응답 변환 실패", e);
            throw unavailable();
        }

        // 5. 응답 내용을 검증해서 결과로 반환
        return validate(response);
    }

    // AI 응답이 올바른지 검증하고 결과로 변환 (하나라도 어긋나면 503 예외를 던짐)
    private AiEvaluationResult validate(AiEvaluationResponse response) {
        // 1. success가 true인지 확인
        if (response == null || !Boolean.TRUE.equals(response.success())) {
            log.error("AI 응답이 성공이 아님: {}", response);
            throw unavailable();
        }

        // 2. 점수가 0~100 사이인지 확인
        Integer score = response.score();
        if (score == null || score < 0 || score > 100) {
            log.error("AI 점수가 올바르지 않음: {}", score);
            throw unavailable();
        }

        // 3. 피드백이 비어 있지 않은지 확인
        if (response.llmFeedback() == null || response.llmFeedback().isBlank()) {
            log.error("AI 피드백이 비어 있음");
            throw unavailable();
        }

        // 4. 검증된 값으로 결과를 만들어 반환
        return new AiEvaluationResult(score, response.llmFeedback(), toJudgements(response.keywords()));
    }

    private List<AiEvaluationResult.KeywordJudgement> toJudgements(List<AiEvaluationResponse.Keyword> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream()
                .filter(keyword -> keyword != null && keyword.keyword() != null)
                .map(keyword -> new AiEvaluationResult.KeywordJudgement(keyword.keyword(), Boolean.TRUE.equals(keyword.isIncluded())))
                .toList();
    }

    private boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String body) {
        if (body == null || body.length() <= MAX_LOG_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_LOG_BODY_LENGTH) + "...";
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    private BusinessException timeout() {
        return new BusinessException(HttpStatus.GATEWAY_TIMEOUT, "evaluation_timeout");
    }
}
