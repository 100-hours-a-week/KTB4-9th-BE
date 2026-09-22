package com.cosmos.cosmos_backend.approach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 실제 소켓으로 타임아웃과 연결 거부가 503/504로 바뀌는지 확인. */
class AiEvaluationClientRealHttpTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiEvaluationClient clientFor(int port, Duration readTimeout) {
        AiClientProperties properties = new AiClientProperties("http://localhost:" + port, Duration.ofMillis(500), readTimeout);
        return new AiEvaluationClient(new AiClientConfig().aiRestClient(properties));
    }

    private AiEvaluationRequest request() {
        return new AiEvaluationRequest(
                "계단 오르기", "본문", "DP", "점화식 구조",
                List.of("점화식"),
                List.of(new ProblemDetailResponse.InputConstraint("N", "INPUT", "INT", 1L, 100000L, List.of())),
                List.of(new ProblemDetailResponse.ExecutionLimit("PYTHON", 3000, 262144)),
                "점화식으로 푼다"
        );
    }

    private int startServer(long delayMs, String responseBody, AtomicReference<String> receivedBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/llm/evaluation", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    @Test
    void evaluate_worksOverRealHttp_andSendsExpectedJsonFields() throws IOException {
        // Given
        AtomicReference<String> received = new AtomicReference<>();
        int port = startServer(0, """
                {"success":true,"score":90,"llmFeedback":"좋아요","keywords":[{"keyword":"점화식","isIncluded":true}]}
                """, received);

        // When
        AiEvaluationResult result = clientFor(port, Duration.ofSeconds(2)).evaluate(request());

        // Then
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.keywords()).containsExactly(new AiEvaluationResult.KeywordJudgement("점화식", true));
        assertThat(received.get())
                .contains("\"problemTitle\":\"계단 오르기\"", "\"category\":\"DP\"", "\"naturalSolution\":\"점화식으로 푼다\"",
                        "\"solutionKeywords\":[\"점화식\"]", "\"memoryLimitKb\":262144");
    }

    @Test
    void evaluate_throws504_whenAiIsSlowerThanReadTimeout() throws IOException {
        int port = startServer(1000, "{\"success\":true,\"score\":90,\"llmFeedback\":\"늦은 응답\"}", new AtomicReference<>());

        assertThatThrownBy(() -> clientFor(port, Duration.ofMillis(200)).evaluate(request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(e.getMessage()).isEqualTo("evaluation_timeout");
                });
    }

    @Test
    void evaluate_throws503_whenNothingListensOnPort() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> clientFor(closedPort, Duration.ofSeconds(2)).evaluate(request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo("ai_server_unavailable");
                });
    }
}
