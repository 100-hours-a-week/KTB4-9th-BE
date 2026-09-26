package com.cosmos.cosmos_backend.problem.controller;

import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemStockResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemStockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProblemStockControllerTest {

    @Mock
    private ProblemStockService problemStockService;

    private MockMvcTester mvc() {
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ProblemStockController(problemStockService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build());
    }

    @Test
    void getUnsolvedProblemCounts_returns200WithSpecShape() {
        // Given
        when(problemStockService.getUnsolvedProblemCounts()).thenReturn(new ProblemStockResponse(List.of(
                new ProblemStockResponse.ProblemCount(Difficulty.LV2, Category.DP, 3),
                new ProblemStockResponse.ProblemCount(Difficulty.LV3, Category.ARRAY, 0)
        )));

        // When & Then
        var body = mvc().get().uri("/problems/new")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("아무도 풀지 않은 문제 조회에 성공했습니다.");
        body.extractingPath("$.data.problemCounts[0].difficulty").isEqualTo("LV2");
        body.extractingPath("$.data.problemCounts[0].category").isEqualTo("DP");
        body.extractingPath("$.data.problemCounts[0].count").isEqualTo(3);
        body.extractingPath("$.data.problemCounts[1].count").isEqualTo(0);
    }
}
