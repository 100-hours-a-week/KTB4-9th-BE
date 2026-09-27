package com.cosmos.cosmos_backend.codesubmission.client;

import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.RunningLimit;
import com.cosmos.cosmos_backend.problem.domain.entity.TestCase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Judge0 서버에 코드 채점을 요청하고 테스트케이스별 판정을 받아오는 클라이언트. */
@Slf4j
@Component
public class Judge0Client {

    private static final String BATCH_PATH = "/submissions/batch";

    private final RestClient judge0RestClient;
    private final Judge0Properties properties;

    public Judge0Client(@Qualifier("judge0RestClient") RestClient judge0RestClient, Judge0Properties properties) {
        this.judge0RestClient = judge0RestClient;
        this.properties = properties;
    }

    /** 테스트케이스마다 판정을 받아 테스트케이스 순서대로 반환. 실패하면 503. */
    public List<JudgingResult> judge(Language language, String sourceCode, List<TestCase> testCases, RunningLimit limit) {
        List<JudgingResult> verdicts = new ArrayList<>();
        try {
            // 1. 테스트케이스를 Judge0 배치 한도씩 나눠서 처리
            for (int from = 0; from < testCases.size(); from += properties.batchSize()) {
                List<TestCase> chunk = testCases.subList(from, Math.min(from + properties.batchSize(), testCases.size()));

                // 2. 채점을 요청하고 토큰을 받음
                List<String> tokens = submitBatch(language, sourceCode, chunk, limit);

                // 3. 모든 채점이 끝날 때까지 결과를 조회
                List<Judge0BatchResult.Item> results = waitForResults(tokens);

                // 4. 상태 번호를 우리 판정으로 바꿔서 순서대로 담음
                results.forEach(item -> verdicts.add(toJudgingResult(item.statusId())));
            }
            return verdicts;
        } catch (ResourceAccessException e) {
            // 5. 연결 실패 또는 시간 초과 (503 예외를 던짐)
            log.error("Judge0 연결 실패 또는 시간 초과", e);
            throw unavailable();
        } catch (RestClientException e) {
            // 6. Judge0가 오류 응답을 주었거나 응답을 읽지 못한 경우 (503 예외를 던짐)
            log.error("Judge0 오류 응답", e);
            throw unavailable();
        }
    }

    // 테스트케이스들을 한 번에 제출하고 토큰 목록을 받음
    private List<String> submitBatch(Language language, String sourceCode, List<TestCase> chunk, RunningLimit limit) {
        // 1. 이 언어의 Judge0 언어 번호를 찾음 (설정에 없으면 503 예외를 던짐)
        Integer languageId = properties.languageIds().get(language);
        if (languageId == null) {
            log.error("Judge0 언어 번호 설정이 없음: {}", language);
            throw unavailable();
        }

        // 2. 테스트케이스마다 제출 내용을 만듦 (제한 값이 없으면 Judge0 기본값이 적용됨)
        List<Judge0BatchRequest.Submission> submissions = chunk.stream()
                .map(testCase -> new Judge0BatchRequest.Submission(
                        languageId,
                        base64(sourceCode),
                        base64(testCase.getInput()),
                        base64(testCase.getExpectedOutput()),
                        limit == null ? null : limit.getTimeLimitMs() / 1000.0,
                        limit == null ? null : Math.min(limit.getMemoryLimitMb() * 1024, properties.maxMemoryKb())
                ))
                .toList();

        // 3. 배치로 제출하고 토큰을 받음
        List<Judge0Token> tokens = judge0RestClient.post()
                .uri(BATCH_PATH + "?base64_encoded=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Judge0BatchRequest(submissions))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Judge0Token>>() {
                });

        // 4. 요청한 개수만큼 토큰이 오지 않았으면 오류로 처리 (503 예외를 던짐)
        if (tokens == null || tokens.size() != chunk.size()) {
            log.error("Judge0 토큰 개수가 요청과 다름: {}", tokens);
            throw unavailable();
        }
        return tokens.stream().map(Judge0Token::token).toList();
    }

    // 모든 채점이 끝날 때까지 일정 간격으로 결과를 조회
    private List<Judge0BatchResult.Item> waitForResults(List<String> tokens) {
        long deadline = System.nanoTime() + properties.maxWait().toNanos();
        while (true) {
            // 1. 토큰들의 현재 상태를 조회
            Judge0BatchResult batch = judge0RestClient.get()
                    .uri(uri -> uri.path(BATCH_PATH)
                            .queryParam("tokens", String.join(",", tokens))
                            .queryParam("base64_encoded", "true")
                            .queryParam("fields", "token,status_id")
                            .build())
                    .retrieve()
                    .body(Judge0BatchResult.class);

            // 2. 전부 끝났으면(상태 3 이상) 토큰 순서대로 정리해서 반환
            if (isAllFinished(batch, tokens.size())) {
                Map<String, Judge0BatchResult.Item> byToken = batch.submissions().stream()
                        .collect(Collectors.toMap(Judge0BatchResult.Item::token, item -> item, (first, second) -> first));
                if (byToken.keySet().containsAll(tokens)) {
                    return tokens.stream().map(byToken::get).toList();
                }
                log.error("Judge0 결과의 토큰이 요청과 다름: {}", batch);
                throw unavailable();
            }

            // 3. 최대 대기 시간을 넘겼으면 503 예외를 던짐
            if (System.nanoTime() > deadline) {
                log.error("Judge0 채점 대기 시간 초과: tokens={}", tokens);
                throw unavailable();
            }

            // 4. 잠깐 쉬었다가 다시 조회
            sleep(properties.pollInterval());
        }
    }

    // 모든 제출이 채점을 마쳤는지 확인
    private boolean isAllFinished(Judge0BatchResult batch, int expectedCount) {
        return batch != null
                && batch.submissions() != null
                && batch.submissions().size() == expectedCount
                && batch.submissions().stream().allMatch(item -> item.statusId() != null && item.statusId() > 2);
    }

    // Judge0 상태 번호를 우리 판정으로 변환 (그 외는 Judge0 내부 오류라 503)
    private JudgingResult toJudgingResult(int statusId) {
        return switch (statusId) {
            case 3 -> JudgingResult.CORRECT;
            case 4 -> JudgingResult.WRONG_ANSWER;
            case 5 -> JudgingResult.TIME_LIMIT_EXCEEDED;
            case 6 -> JudgingResult.COMPILE_ERROR;
            case 7, 8, 9, 10, 11, 12 -> JudgingResult.RUNTIME_ERROR;
            default -> {
                log.error("Judge0 내부 오류 상태: {}", statusId);
                throw unavailable();
            }
        };
    }

    // 문자열을 base64로 인코딩
    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    // 지정한 시간만큼 쉼
    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Judge0 결과 대기 중 인터럽트", e);
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable");
    }
}
