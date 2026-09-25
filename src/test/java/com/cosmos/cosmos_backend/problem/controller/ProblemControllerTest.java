package com.cosmos.cosmos_backend.problem.controller;

import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProblemControllerTest {

    @Mock
    private ProblemService problemService;

    private MockMvcTester mvc() {
        ProblemController controller = new ProblemController(problemService);
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build());
    }

    // 로그인한 사용자(sub = userId)가 있는 상태를 만든다.
    private void loginAs(String userId) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(userId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getProblemDetail_returns200WithBody_whenProblemExists() {
        // Given
        ProblemDetailResponse response = new ProblemDetailResponse(
                1L, Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용",
                "입력 형식", "출력 형식", List.of(), List.of(), List.of(), 1
        );
        loginAs("7");
        when(problemService.getProblemDetail(7L, 1L)).thenReturn(response);

        // When & Then
        var body = mvc().get().uri("/problems/1")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("problem_detail_retrieval_success");
        // 명세서: data 안에 problem 한 겹이 더 있음
        body.extractingPath("$.data.problem.problemId").isEqualTo(1);
        body.extractingPath("$.data.problem.usedHintStage").isEqualTo(1);
    }

    @Test
    void getProblemDetail_returns404_whenServiceThrowsNotFound() {
        // Given
        loginAs("7");
        when(problemService.getProblemDetail(7L, 999L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // When & Then
        mvc().get().uri("/problems/999")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_not_found");
    }

    @Test
    void getProblemDetail_returns400_whenProblemIdNotNumeric() {
        loginAs("7");
        mvc().get().uri("/problems/abc")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_problem_id");
    }
}
