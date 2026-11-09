package com.cosmos.cosmos_backend.approach.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.dto.response.CategoryAnswerResponse;
import com.cosmos.cosmos_backend.approach.dto.response.EvaluationAcceptedResponse;
import com.cosmos.cosmos_backend.approach.service.ApproachSubmissionService;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationTaskService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import java.time.Instant;
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

    @Mock
    private SolutionEvaluationTaskService solutionEvaluationTaskService;

    private MockMvcTester mvc() {
        ApproachSubmissionController controller = new ApproachSubmissionController(approachSubmissionService, solutionEvaluationTaskService);
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
    void submit_returns202_andPassesTokenUserIdToService() {
        // Given
        loginAs("42");
        when(solutionEvaluationTaskService.accept(42L, 1L, "ARRAY", "풀이")).thenReturn(new EvaluationAcceptedResponse(
                9001L, 1L, "PENDING", Instant.parse("2026-10-19T03:30:00Z"), ApproachSubmitResponse.SubmissionUsage.of(2)));

        // When & Then
        var body = mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("solution_evaluation_accepted");
        body.extractingPath("$.data.evaluationTaskId").isEqualTo(9001);
        body.extractingPath("$.data.problemId").isEqualTo(1);
        body.extractingPath("$.data.status").isEqualTo("PENDING");
        body.extractingPath("$.data.createdAt").isEqualTo("2026-10-19T03:30:00Z");
        body.extractingPath("$.data.submissionUsage.limit").isEqualTo(5);
        body.extractingPath("$.data.submissionUsage.usedCount").isEqualTo(2);
        body.extractingPath("$.data.submissionUsage.remainingCount").isEqualTo(3);

        verify(solutionEvaluationTaskService).accept(42L, 1L, "ARRAY", "풀이");
    }

    @Test
    void submit_returns409WithTaskInfo_whenEvaluationAlreadyInProgress() {
        // Given
        loginAs("42");
        when(solutionEvaluationTaskService.accept(42L, 1L, "ARRAY", "풀이")).thenThrow(new BusinessException(
                HttpStatus.CONFLICT, "solution_evaluation_already_in_progress", new EvaluationAcceptedResponse.InProgressData(9001L, "PROCESSING")));

        // When & Then
        var body = mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("solution_evaluation_already_in_progress");
        body.extractingPath("$.data.evaluationTaskId").isEqualTo(9001);
        body.extractingPath("$.data.status").isEqualTo("PROCESSING");
    }

    @Test
    void submit_returns429WithNestedUsage_whenLimitExceeded() {
        // Given
        loginAs("42");
        when(solutionEvaluationTaskService.accept(42L, 1L, "ARRAY", "풀이")).thenThrow(new BusinessException(
                HttpStatus.TOO_MANY_REQUESTS, "solution_submission_limit_exceeded",
                new ApproachSubmitResponse.SubmissionLimitExceededData(1L, ApproachSubmitResponse.SubmissionUsage.of(5))));

        // When & Then
        var body = mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("solution_submission_limit_exceeded");
        body.extractingPath("$.data.problemId").isEqualTo(1);
        body.extractingPath("$.data.submissionUsage.limit").isEqualTo(5);
        body.extractingPath("$.data.submissionUsage.usedCount").isEqualTo(5);
        body.extractingPath("$.data.submissionUsage.remainingCount").isEqualTo(0);
    }

    @Test
    void submit_returns404_whenProblemNotFound() {
        // Given
        loginAs("42");
        when(solutionEvaluationTaskService.accept(42L, 1L, "ARRAY", "풀이"))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // When & Then
        mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_not_found");
    }

    @Test
    void submit_returns400_whenSelectedCategoryIsInvalid() {
        // Given
        loginAs("42");
        when(solutionEvaluationTaskService.accept(42L, 1L, "NOT_A_CATEGORY", "풀이"))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "invalid_selected_category"));

        // When & Then
        mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"NOT_A_CATEGORY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_selected_category");
    }

    @Test
    void getCategoryAnswer_returns200() {
        // Given
        loginAs("42");
        when(approachSubmissionService.getCategoryAnswer(42L, 1L)).thenReturn(new CategoryAnswerResponse(1L, "ARRAY", "선정 배경"));

        // When & Then
        var body = mvc().get().uri("/problems/1/solution-submissions/category-answer")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("category_answer_success");
        body.extractingPath("$.data.problemId").isEqualTo(1);
        body.extractingPath("$.data.correctCategory").isEqualTo("ARRAY");
        body.extractingPath("$.data.categorySelectReason").isEqualTo("선정 배경");
    }

    @Test
    void getCategoryAnswer_returns403_whenNeverSubmitted() {
        // Given
        loginAs("42");
        when(approachSubmissionService.getCategoryAnswer(42L, 1L))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "category_answer_not_available"));

        // When & Then
        mvc().get().uri("/problems/1/solution-submissions/category-answer")
                .assertThat()
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("category_answer_not_available");
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
    void submit_returns400_whenSelectedCategoryBlank() {
        loginAs("42");

        mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("selected_category_is_required");
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
