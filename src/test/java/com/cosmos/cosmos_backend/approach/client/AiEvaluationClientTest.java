package com.cosmos.cosmos_backend.approach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiEvaluationClientTest {

    private static final String URL = "http://ai.test/api/llm/evaluation";

    private MockRestServiceServer server;
    private AiEvaluationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiEvaluationClient(builder.build());
    }

    private AiEvaluationRequest request() {
        return new AiEvaluationRequest(
                "계단 오르기", "본문", "DP", "점화식 구조",
                List.of("점화식", "모듈러 연산"),
                List.of(new ProblemDetailResponse.InputConstraint("N", "INPUT", "INT", 1L, 100000L, List.of())),
                List.of(new ProblemDetailResponse.ExecutionLimit("PYTHON", 3000, 262144)),
                "점화식으로 푼다"
        );
    }

    private void respond200(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertFailsWith(HttpStatus status, String message) {
        assertThatThrownBy(() -> client.evaluate(request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }

    @Test
    void evaluate_sendsExpectedRequest_andReturnsResult() {
        // Given
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.problemTitle").value("계단 오르기"))
                .andExpect(jsonPath("$.problemDescription").value("본문"))
                .andExpect(jsonPath("$.category").value("DP"))
                .andExpect(jsonPath("$.categorySelectReason").value("점화식 구조"))
                .andExpect(jsonPath("$.solutionKeywords[1]").value("모듈러 연산"))
                .andExpect(jsonPath("$.inputConstraints[0].target").value("N"))
                .andExpect(jsonPath("$.executionLimits[0].memoryLimitKb").value(262144))
                .andExpect(jsonPath("$.naturalSolution").value("점화식으로 푼다"))
                .andRespond(withSuccess("""
                        {"success":true,"message":"ok","score":85,"llmFeedback":"좋아요",
                         "keywords":[{"keyword":"점화식","isIncluded":true},{"keyword":"모듈러 연산","isIncluded":false}]}
                        """, MediaType.APPLICATION_JSON));

        // When
        AiEvaluationResult result = client.evaluate(request());

        // Then
        assertThat(result.score()).isEqualTo(85);
        assertThat(result.feedback()).isEqualTo("좋아요");
        assertThat(result.keywords()).containsExactly(
                new AiEvaluationResult.KeywordJudgement("점화식", true),
                new AiEvaluationResult.KeywordJudgement("모듈러 연산", false)
        );
        server.verify();
    }

    @Test
    void evaluate_returnsEmptyKeywords_whenKeywordsMissing() {
        respond200("{\"success\":true,\"score\":0,\"llmFeedback\":\"피드백\"}");

        AiEvaluationResult result = client.evaluate(request());

        assertThat(result.score()).isZero();
        assertThat(result.keywords()).isEmpty();
    }

    @Test
    void evaluate_treatsNullIsIncludedAsFalse_andSkipsBrokenEntries() {
        respond200("""
                {"success":true,"score":50,"llmFeedback":"피드백",
                 "keywords":[{"keyword":"점화식"},{"isIncluded":true},null]}
                """);

        AiEvaluationResult result = client.evaluate(request());

        assertThat(result.keywords()).containsExactly(new AiEvaluationResult.KeywordJudgement("점화식", false));
    }

    @Test
    void evaluate_ignoresUnknownFields() {
        respond200("{\"success\":true,\"score\":10,\"llmFeedback\":\"피드백\",\"extra\":\"무시\"}");

        assertThat(client.evaluate(request()).score()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "null"})
    void evaluate_throws503_whenScoreInvalid(String score) {
        respond200("{\"success\":true,\"score\":" + score + ",\"llmFeedback\":\"피드백\"}");

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    void evaluate_throws503_whenFeedbackBlank(String feedback) {
        respond200("{\"success\":true,\"score\":50,\"llmFeedback\":" + feedback + "}");

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    @Test
    void evaluate_throws503_whenSuccessFalse() {
        respond200("{\"success\":false,\"error_code\":\"MODEL_UNAVAILABLE\",\"message\":\"장애\"}");

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    @Test
    void evaluate_throws503_whenBodyIsBrokenJson() {
        respond200("{not json");

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    @Test
    void evaluate_throws503_whenBodyIsEmpty() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 422, 500, 503})
    void evaluate_throws503_whenAiReturnsErrorStatus(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"error_code\":\"INVALID_CATEGORY\",\"message\":\"오류\"}"));

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    @Test
    void evaluate_throws503_whenConnectionFails() {
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }

    @Test
    void evaluate_throws504_whenSocketTimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertFailsWith(HttpStatus.GATEWAY_TIMEOUT, "evaluation_timeout");
    }

    @Test
    void evaluate_throws504_whenHttpClientTimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new HttpTimeoutException("request timed out")));

        assertFailsWith(HttpStatus.GATEWAY_TIMEOUT, "evaluation_timeout");
    }

    @Test
    void evaluate_throws503_whenOtherIoError() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("connection reset")));

        assertFailsWith(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable");
    }
}
