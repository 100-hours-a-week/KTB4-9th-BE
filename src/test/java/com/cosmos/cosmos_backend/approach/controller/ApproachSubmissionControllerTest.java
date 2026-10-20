package com.cosmos.cosmos_backend.approach.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.ApproachSubmitResult;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.dto.response.CategoryAnswerResponse;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
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
        ApproachSubmitResult result = new ApproachSubmitResult(submission, List.of(new AiEvaluationResult.KeywordJudgement("정렬", true)), problem());
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
        body.extractingPath("$.data.submissionUsage.limit").isEqualTo(5);
        body.extractingPath("$.data.submissionUsage.usedCount").isEqualTo(1);
        body.extractingPath("$.data.submissionUsage.remainingCount").isEqualTo(4);
        body.extractingPath("$.data.result.correctCategory").isEqualTo("ARRAY");
        body.extractingPath("$.data.result.categorySelectReason").isEqualTo("선정 배경");

        verify(approachSubmissionService).submit(42L, 1L, "ARRAY", "풀이");
    }

    @Test
    void submit_hidesCorrectCategory_whenWrongAndLimitRemains() {
        // Given
        loginAs("42");
        ApproachSubmission submission = new ApproachSubmission(42L, 1L, Category.DP, "풀이", false, 40, "아쉬워요");
        when(approachSubmissionService.submit(42L, 1L, "DP", "풀이")).thenReturn(new ApproachSubmitResult(submission, List.of(), problem()));

        // When & Then
        var body = mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"DP\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.data.result.correctCategory").isNull();
        body.extractingPath("$.data.result.categorySelectReason").isNull();
    }

    @Test
    void submit_revealsCorrectCategory_whenWrongButLimitReached() {
        // Given
        loginAs("42");
        ApproachSubmission submission = new ApproachSubmission(42L, 1L, Category.DP, "풀이", false, 40, "아쉬워요");
        for (int i = 1; i < ApproachSubmission.MAX_SUBMISSION_COUNT; i++) {
            submission.resubmit(Category.DP, "풀이", false, 40, "아쉬워요");
        }
        when(approachSubmissionService.submit(42L, 1L, "DP", "풀이")).thenReturn(new ApproachSubmitResult(submission, List.of(), problem()));

        // When & Then
        var body = mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"DP\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.data.result.correctCategory").isEqualTo("ARRAY");
        body.extractingPath("$.data.result.categorySelectReason").isEqualTo("선정 배경");
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

    private Problem problem() {
        return new Problem(Difficulty.LV1, Category.ARRAY, "제목", "내용", "입력 형식", "출력 형식", List.<AiProblemsCreateRequestDto.InputConstraints>of(), "선정 배경");
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

    @Test
    void submit_returns429WithUsageData_whenSubmissionLimitExceeded() {
        // Given
        loginAs("42");
        when(approachSubmissionService.submit(42L, 1L, "ARRAY", "풀이"))
                .thenThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "solution_submission_limit_exceeded",
                        new ApproachSubmitResponse.SubmissionLimitExceededData(1L, 5, 5, 0)));

        // When & Then
        var body = mvc().post().uri("/problems/1/solution-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedCategory\":\"ARRAY\",\"natural_solution\":\"풀이\"}")
                .assertThat()
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("solution_submission_limit_exceeded");
        body.extractingPath("$.data.problemId").isEqualTo(1);
        body.extractingPath("$.data.limit").isEqualTo(5);
        body.extractingPath("$.data.usedCount").isEqualTo(5);
        body.extractingPath("$.data.remainingCount").isEqualTo(0);
    }
}
