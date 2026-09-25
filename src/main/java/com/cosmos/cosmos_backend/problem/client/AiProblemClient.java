package com.cosmos.cosmos_backend.problem.client;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemCreateOndemandRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.AiProblemCreateOndemandResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiProblemClient {

    private final RestClient restClient;

    public AiProblemClient(
            RestClient.Builder restClientBuilder,
            @Value("${ai.root-url}") String aiRootUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(aiRootUrl)
                .build();
    }

    // 온디맨드 문제 생성 요청
    public AiProblemCreateOndemandResponseDto createProblem(
            Difficulty difficulty,
            Category category
    ) {

        // 1. AI 요청 Body 생성
        AiProblemCreateOndemandRequestDto request =
                new AiProblemCreateOndemandRequestDto(
                        difficulty,
                        category
                );

        // 2. AI 서버에 문제 생성 요청
        AiProblemCreateOndemandResponseDto response =
                restClient.post()
                        .uri("/problem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(AiProblemCreateOndemandResponseDto.class);

        // 3. AI 응답 반환
        return response;
    }
}