package com.cosmos.cosmos_backend.codesubmission.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.RunningLimit;
import com.cosmos.cosmos_backend.problem.domain.entity.TestCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 진짜 Judge0 서버 없이, 테스트 안에서 띄운 가짜 Judge0 서버로 요청 조립·폴링·판정 변환을 확인. */
class Judge0ClientTest {

    private static final Map<Language, Integer> LANGUAGE_IDS = Map.of(
            Language.PYTHON, 71, Language.JAVASCRIPT, 63, Language.JAVA, 62, Language.CPP, 54
    );

    private FakeJudge0 fake;

    @AfterEach
    void tearDown() {
        if (fake != null) {
            fake.stop();
        }
    }

    private Judge0Client clientFor(String baseUrl, Duration maxWait, Map<Language, Integer> languageIds) {
        Judge0Properties properties = new Judge0Properties(
                baseUrl, Duration.ofMillis(500), Duration.ofSeconds(2),
                Duration.ofMillis(10), maxWait, 20, 512000, languageIds
        );
        return new Judge0Client(new Judge0Config().judge0RestClient(properties), properties);
    }

    private Judge0Client client() {
        return clientFor("http://localhost:" + fake.port(), Duration.ofMillis(500), LANGUAGE_IDS);
    }

    private List<TestCase> testCases(int count) {
        List<TestCase> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new TestCase(1L, "입력" + i, "출력" + i, i));
        }
        return list;
    }

    private String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessage("judge_server_unavailable");
    }

    @Test
    void judge_sendsBase64FieldsLanguageIdAndLimits_andReturnsVerdictsInOrder() throws IOException {
        // Given: 1번째 통과(3), 2번째 오답(4)
        fake = new FakeJudge0(index -> index == 1 ? 3 : 4);
        RunningLimit limit = new RunningLimit(1L, Language.PYTHON, 3000F, 256);

        // When
        List<JudgingResult> verdicts = client().judge(Language.PYTHON, "print('안녕')", testCases(2), limit);

        // Then
        assertThat(verdicts).containsExactly(JudgingResult.CORRECT, JudgingResult.WRONG_ANSWER);
        assertThat(fake.postQueries).containsExactly("base64_encoded=true");
        String body = fake.postBodies.get(0);
        assertThat(body).contains("\"language_id\":71");
        assertThat(body).contains("\"source_code\":\"" + b64("print('안녕')") + "\"");
        assertThat(body).contains("\"stdin\":\"" + b64("입력1") + "\"", "\"expected_output\":\"" + b64("출력1") + "\"");
        assertThat(body).contains("\"stdin\":\"" + b64("입력2") + "\"", "\"expected_output\":\"" + b64("출력2") + "\"");
        assertThat(body).contains("\"cpu_time_limit\":3.0", "\"memory_limit\":262144");
    }

    @Test
    void judge_usesJudge0DefaultLimits_whenNoRunningLimit() throws IOException {
        fake = new FakeJudge0(index -> 3);

        client().judge(Language.JAVA, "class Main {}", testCases(1), null);

        assertThat(fake.postBodies.get(0)).doesNotContain("cpu_time_limit", "memory_limit");
    }

    @Test
    void judge_capsMemoryLimitAtServerMaximum() throws IOException {
        // Given: 600MB = 614400KB, 서버 최대치는 512000KB
        fake = new FakeJudge0(index -> 3);
        RunningLimit limit = new RunningLimit(1L, Language.JAVA, 2000F, 600);

        client().judge(Language.JAVA, "class Main {}", testCases(1), limit);

        assertThat(fake.postBodies.get(0)).contains("\"memory_limit\":512000");
    }

    @Test
    void judge_sendsConfiguredLanguageIdForEachLanguage() throws IOException {
        fake = new FakeJudge0(index -> 3);

        for (Language language : Language.values()) {
            client().judge(language, "code", testCases(1), null);
        }

        assertThat(fake.postBodies).hasSize(4);
        assertThat(fake.postBodies.get(0)).contains("\"language_id\":71");   // PYTHON
        assertThat(fake.postBodies.get(1)).contains("\"language_id\":62");   // JAVA
        assertThat(fake.postBodies.get(2)).contains("\"language_id\":63");   // JAVASCRIPT
        assertThat(fake.postBodies.get(3)).contains("\"language_id\":54");   // CPP
    }

    @Test
    void judge_keepsPollingUntilAllFinished() throws IOException {
        // Given: 처음 2번의 조회는 "처리 중(2)"
        fake = new FakeJudge0(index -> 3);
        fake.processingPolls = 2;

        // When
        List<JudgingResult> verdicts = client().judge(Language.PYTHON, "code", testCases(2), null);

        // Then
        assertThat(verdicts).containsExactly(JudgingResult.CORRECT, JudgingResult.CORRECT);
        assertThat(fake.getCount.get()).isEqualTo(3);
    }

    @Test
    void judge_mapsEveryJudge0StatusToVerdict() throws IOException {
        // Given: 테스트케이스 10개가 각각 상태 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
        fake = new FakeJudge0(index -> index + 2);

        // When
        List<JudgingResult> verdicts = client().judge(Language.PYTHON, "code", testCases(10), null);

        // Then
        assertThat(verdicts).containsExactly(
                JudgingResult.CORRECT, JudgingResult.WRONG_ANSWER, JudgingResult.TIME_LIMIT_EXCEEDED,
                JudgingResult.COMPILE_ERROR, JudgingResult.RUNTIME_ERROR, JudgingResult.RUNTIME_ERROR,
                JudgingResult.RUNTIME_ERROR, JudgingResult.RUNTIME_ERROR, JudgingResult.RUNTIME_ERROR,
                JudgingResult.RUNTIME_ERROR
        );
    }

    @Test
    void judge_splitsTestCasesIntoBatchesOfTwenty() throws IOException {
        // Given: 21개 (마지막 21번째만 오답)
        fake = new FakeJudge0(index -> index == 21 ? 4 : 3);

        // When
        List<JudgingResult> verdicts = client().judge(Language.PYTHON, "code", testCases(21), null);

        // Then: 20개 + 1개로 두 번 요청하고, 결과는 21개가 순서대로
        assertThat(fake.postBodies).hasSize(2);
        assertThat(countOccurrences(fake.postBodies.get(0), "\"language_id\"")).isEqualTo(20);
        assertThat(countOccurrences(fake.postBodies.get(1), "\"language_id\"")).isEqualTo(1);
        assertThat(verdicts).hasSize(21);
        assertThat(verdicts.get(19)).isEqualTo(JudgingResult.CORRECT);
        assertThat(verdicts.get(20)).isEqualTo(JudgingResult.WRONG_ANSWER);
    }

    @Test
    void judge_throws503_whenJudge0ReturnsError() throws IOException {
        fake = new FakeJudge0(index -> 3);
        fake.postStatus = 500;

        assertUnavailable(() -> client().judge(Language.PYTHON, "code", testCases(1), null));
    }

    @Test
    void judge_throws503_whenConnectionRefused() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        Judge0Client refused = clientFor("http://localhost:" + freePort, Duration.ofMillis(500), LANGUAGE_IDS);

        assertUnavailable(() -> refused.judge(Language.PYTHON, "code", testCases(1), null));
    }

    @Test
    void judge_throws503_whenNeverFinishesWithinMaxWait() throws IOException {
        // Given: 계속 "처리 중(2)"
        fake = new FakeJudge0(index -> 3);
        fake.processingPolls = Integer.MAX_VALUE;

        assertUnavailable(() -> client().judge(Language.PYTHON, "code", testCases(1), null));
    }

    @Test
    void judge_throws503_whenInternalErrorStatus() throws IOException {
        // Given: 상태 13 (Internal Error)
        fake = new FakeJudge0(index -> 13);

        assertUnavailable(() -> client().judge(Language.PYTHON, "code", testCases(1), null));
    }

    @Test
    void judge_throws503_whenTokenCountDiffersFromRequest() throws IOException {
        fake = new FakeJudge0(index -> 3);
        fake.tokenCountDelta = -1;

        assertUnavailable(() -> client().judge(Language.PYTHON, "code", testCases(2), null));
    }

    @Test
    void judge_throws503_whenLanguageIdNotConfigured() throws IOException {
        fake = new FakeJudge0(index -> 3);
        Judge0Client noLanguage = clientFor("http://localhost:" + fake.port(), Duration.ofMillis(500), Map.of());

        assertUnavailable(() -> noLanguage.judge(Language.PYTHON, "code", testCases(1), null));
        assertThat(fake.postBodies).isEmpty();
    }

    private int countOccurrences(String text, String target) {
        Matcher matcher = Pattern.compile(Pattern.quote(target)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** 테스트 안에서 띄우는 가짜 Judge0. 토큰은 요청 순서대로 tok-1, tok-2, ...이고 상태는 토큰 번호로 정함. */
    private static class FakeJudge0 {

        final List<String> postBodies = Collections.synchronizedList(new ArrayList<>());
        final List<String> postQueries = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger getCount = new AtomicInteger();
        final AtomicInteger tokenSeq = new AtomicInteger();
        volatile int processingPolls = 0;
        volatile int postStatus = 200;
        volatile int tokenCountDelta = 0;

        private final HttpServer server;
        private final IntFunction<Integer> statusOfToken;

        FakeJudge0(IntFunction<Integer> statusOfToken) throws IOException {
            this.statusOfToken = statusOfToken;
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/submissions/batch", this::handle);
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                handlePost(exchange);
            } else {
                handleGet(exchange);
            }
        }

        // 제출 개수만큼 토큰을 만들어 돌려줌
        private void handlePost(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            postBodies.add(body);
            postQueries.add(exchange.getRequestURI().getRawQuery());
            if (postStatus != 200) {
                respond(exchange, postStatus, "{\"error\":\"boom\"}");
                return;
            }
            int submitted = 0;
            Matcher matcher = Pattern.compile("\"language_id\"").matcher(body);
            while (matcher.find()) {
                submitted++;
            }
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < submitted + tokenCountDelta; i++) {
                json.append(i == 0 ? "" : ",").append("{\"token\":\"tok-").append(tokenSeq.incrementAndGet()).append("\"}");
            }
            respond(exchange, 200, json.append("]").toString());
        }

        // 조회한 토큰들의 상태를 돌려줌 (처음 processingPolls번은 처리 중)
        private void handleGet(HttpExchange exchange) throws IOException {
            boolean processing = getCount.incrementAndGet() <= processingPolls;
            String query = exchange.getRequestURI().getRawQuery();
            String tokens = Pattern.compile("tokens=([^&]*)").matcher(query).results()
                    .map(result -> result.group(1)).findFirst().orElse("");
            StringBuilder json = new StringBuilder("{\"submissions\":[");
            boolean first = true;
            for (String token : tokens.split(",")) {
                int number = Integer.parseInt(token.replace("tok-", ""));
                int status = processing ? 2 : statusOfToken.apply(number);
                json.append(first ? "" : ",").append("{\"token\":\"").append(token).append("\",\"status_id\":").append(status).append("}");
                first = false;
            }
            respond(exchange, 200, json.append("]}").toString());
        }

        private void respond(HttpExchange exchange, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
