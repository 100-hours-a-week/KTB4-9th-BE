package com.cosmos.cosmos_backend.problem.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemSelectionResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemSelectionService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class ProblemSelectionControllerTest {

    @Mock
    private ProblemSelectionService problemSelectionService;

    private MockMvcTester mvc() {
        ProblemSelectionController controller = new ProblemSelectionController(problemSelectionService);
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
    void selectProblem_returns200WithSpecShape_andPassesUserIdAndParams() {
        // Given
        loginAs("42");
        ProblemSelectionResponse response = new ProblemSelectionResponse(
                new ProblemSelectionResponse.ProblemSummary(
                        7L, 3, Category.DP, "계단 오르기", "내용",
                        List.of(new AiProblemsCreateRequestDto.ProblemExamples("3", "3", null))
                ),
                new ProblemSelectionResponse.DailyUsage(LocalDate.of(2026, 9, 5), 3, 1, 2)
        );
        when(problemSelectionService.select(42L, "3", "RANDOM")).thenReturn(response);

        // When & Then
        var body = mvc().get().uri("/problems?level=3&category=RANDOM")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("problem_selection_success");
        body.extractingPath("$.data.problem.problemId").isEqualTo(7);
        body.extractingPath("$.data.problem.level").isEqualTo(3);
        body.extractingPath("$.data.problem.category").isEqualTo("DP");
        body.extractingPath("$.data.problem.title").isEqualTo("계단 오르기");
        body.extractingPath("$.data.problem.examples[0].input").isEqualTo("3");
        body.extractingPath("$.data.dailyUsage.date").isEqualTo("2026-09-05");
        body.extractingPath("$.data.dailyUsage.limit").isEqualTo(3);
        body.extractingPath("$.data.dailyUsage.usedCount").isEqualTo(1);
        body.extractingPath("$.data.dailyUsage.remainingCount").isEqualTo(2);

        verify(problemSelectionService).select(42L, "3", "RANDOM");
    }

    @Test
    void selectProblem_passesNullParams_whenQueryOmitted() {
        // Given
        loginAs("42");
        when(problemSelectionService.select(42L, null, null))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "problem_level_is_required"));

        // When & Then
        mvc().get().uri("/problems")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_level_is_required");
    }

    @Test
    void selectProblem_returns429WithData_whenLimitExceeded() {
        // Given
        loginAs("42");
        var data = new ProblemSelectionResponse.DailyLimitExceededData(
                LocalDate.of(2026, 9, 5), 3, 3, 0, OffsetDateTime.of(2026, 9, 6, 0, 0, 0, 0, ZoneOffset.ofHours(9)));
        when(problemSelectionService.select(42L, "3", null))
                .thenThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "daily_problem_limit_exceeded", data));

        // When & Then
        var body = mvc().get().uri("/problems?level=3")
                .assertThat()
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("daily_problem_limit_exceeded");
        body.extractingPath("$.data.limit").isEqualTo(3);
        body.extractingPath("$.data.usedCount").isEqualTo(3);
        body.extractingPath("$.data.remainingCount").isEqualTo(0);
        body.extractingPath("$.data.resetAt").isEqualTo("2026-09-06T00:00:00+09:00");
    }

    @Test
    void selectProblem_returns404_whenNoMatchingProblem() {
        // Given
        loginAs("42");
        when(problemSelectionService.select(42L, "3", "GRAPH"))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "matching_problem_not_found"));

        // When & Then
        mvc().get().uri("/problems?level=3&category=GRAPH")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("matching_problem_not_found");
    }
}
