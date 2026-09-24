package com.cosmos.cosmos_backend.problem.client;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiProblemClient {

    // ai 서버 주소 설정값에서
    @Value("${ai.root-url}")
    private String aiRootUrl;

    private final RestClient restClient = RestClient.create(aiRootUrl);

    // 온디맨드 문제 생성 요청 (난이도, 카테고리 지정 부족한 문제 요청)
    public ProblemDetailResponse createProblem(Difficulty difficulty, Category category) {



        return null;
    }
}
