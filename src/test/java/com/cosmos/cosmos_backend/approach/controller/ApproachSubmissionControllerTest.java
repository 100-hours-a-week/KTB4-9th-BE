package com.cosmos.cosmos_backend.approach.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.ApproachSubmitResult;
import com.cosmos.cosmos_backend.approach.service.ApproachSubmissionService;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ApproachSubmissionControllerTest {

    @Mock
    private ApproachSubmissionService approachSubmissionService;

    private MockMvcTester mvc() {
        ApproachSubmissionController controller = new ApproachSubmissionController(approachSubmissionService);
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
    void submit_returns200AndPassesTokenUserIdToService() {
        // Given
        loginAs("42");
        ApproachSubmission submission = new ApproachSubmission(42L, 1L, Category.ARRAY, "풀이", true, 85, "좋은 접근이에요");
        ApproachSubmitResult result = new ApproachSubmitResult(submission, List.of(new AiEvaluationResult.KeywordJudgement("정렬", true)));
        when(approachSubmissionService.submit(42L, 1L, "ARRAY", "풀이")).thenReturn(result);

        // When & Then
        var body = mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.data.evaluationStatus").isEqualTo("COMPLETED");
        body.extractingPath("$.data.result.isCorrect").isEqualTo(true);
        body.extractingPath("$.data.result.approachScore").isEqualTo(85);
        body.extractingPath("$.data.result.aiFeedback").isEqualTo("좋은 접근이에요");
        body.extractingPath("$.data.result.keywords[0].keyword").isEqualTo("정렬");
        body.extractingPath("$.data.result.keywords[0].isIncluded").isEqualTo(true);

        verify(approachSubmissionService).submit(42L, 1L, "ARRAY", "풀이");
    }

    @Test
    void submit_returns400_whenProblemIdNotNumeric() {
        loginAs("42");

        mvc().post().uri("/problems/abc/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_problem_id");
    }

    @Test
    void submit_returns400_whenApproachBlank() {
        loginAs("42");

        mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("approach_is_required");
    }

    @Test
    void submit_returns400_whenOldFieldNameApproachUsed() {
        loginAs("42");

        mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"approach\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("approach_is_required");
    }
}
