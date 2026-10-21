package com.cosmos.cosmos_backend.approach.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.dto.response.SolutionEvaluationResponse;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationTaskService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import java.time.Instant;
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
class SolutionEvaluationControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-19T03:30:00Z");

    @Mock
    private SolutionEvaluationTaskService solutionEvaluationTaskService;

    private MockMvcTester mvc() {
        SolutionEvaluationController controller = new SolutionEvaluationController(solutionEvaluationTaskService);
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

    private SolutionEvaluationResponse response(String status, Boolean isLatest, Long submissionId,
                                                ApproachSubmitResponse.Result result, SolutionEvaluationResponse.Failure failure) {
        return new SolutionEvaluationResponse(
                9001L, 101L, status, isLatest, new SolutionEvaluationResponse.LatestEvaluation(9001L, status),
                submissionId, "BINARY_SEARCH", "중간값을 기준으로 탐색 범위를 줄입니다.", result, failure,
                ApproachSubmitResponse.SubmissionUsage.of(2),
                CREATED_AT, CREATED_AT.plusSeconds(1), null, null
        );
    }

    @Test
    void get_returns200WithProgress_whenTaskIsProcessing() {
        // Given
        loginAs("42");
        when(solutionEvaluationTaskService.get(42L, 9001L)).thenReturn(response("PROCESSING", true, null, null, null));

        // When & Then
        var body = mvc().get().uri("/solution-evaluations/9001")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("solution_evaluation_retrieval_success");
        body.extractingPath("$.data.evaluationTaskId").isEqualTo(9001);
        body.extractingPath("$.data.problemId").isEqualTo(101);
        body.extractingPath("$.data.status").isEqualTo("PROCESSING");
        body.extractingPath("$.data.isLatest").isEqualTo(true);
        body.extractingPath("$.data.latestEvaluation.evaluationTaskId").isEqualTo(9001);
        body.extractingPath("$.data.latestEvaluation.status").isEqualTo("PROCESSING");
        body.extractingPath("$.data.submissionId").isNull();
        body.extractingPath("$.data.selectedCategory").isEqualTo("BINARY_SEARCH");
        body.extractingPath("$.data.result").isNull();
        body.extractingPath("$.data.failure").isNull();
        body.extractingPath("$.data.submissionUsage.usedCount").isEqualTo(2);
        body.extractingPath("$.data.createdAt").isEqualTo("2026-10-19T03:30:00Z");
        body.extractingPath("$.data.startedAt").isEqualTo("2026-10-19T03:30:01Z");
        body.extractingPath("$.data.nextRetryAt").isNull();
        body.extractingPath("$.data.completedAt").isNull();

        verify(solutionEvaluationTaskService).get(42L, 9001L);
    }

    @Test
    void get_returns200WithResult_whenTaskSucceeded() {
        // Given
        loginAs("42");
        var result = new ApproachSubmitResponse.Result(true, 90,
                List.of(new ApproachSubmitResponse.KeywordJudgement("이분 탐색", true)), "탐색 범위를 줄이는 접근이 정확합니다.", "BINARY_SEARCH", "선정 배경");
        when(solutionEvaluationTaskService.get(42L, 9001L)).thenReturn(response("SUCCEEDED", true, 501L, result, null));

        // When & Then
        var body = mvc().get().uri("/solution-evaluations/9001")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.data.status").isEqualTo("SUCCEEDED");
        body.extractingPath("$.data.submissionId").isEqualTo(501);
        body.extractingPath("$.data.result.isCorrect").isEqualTo(true);
        body.extractingPath("$.data.result.approachScore").isEqualTo(90);
        body.extractingPath("$.data.result.keywords[0].keyword").isEqualTo("이분 탐색");
        body.extractingPath("$.data.result.keywords[0].isIncluded").isEqualTo(true);
        body.extractingPath("$.data.result.aiFeedback").isEqualTo("탐색 범위를 줄이는 접근이 정확합니다.");
        body.extractingPath("$.data.failure").isNull();
    }

    @Test
    void get_returns200WithFailure_whenTaskFailed() {
        // Given
        loginAs("42");
        var failure = new SolutionEvaluationResponse.Failure("AI_SERVER_UNAVAILABLE", "평가를 완료하지 못했습니다. 다시 제출해 주세요.", true);
        when(solutionEvaluationTaskService.get(42L, 9001L)).thenReturn(response("FAILED", true, null, null, failure));

        // When & Then
        var body = mvc().get().uri("/solution-evaluations/9001")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.data.status").isEqualTo("FAILED");
        body.extractingPath("$.data.failure.code").isEqualTo("AI_SERVER_UNAVAILABLE");
        body.extractingPath("$.data.failure.message").isEqualTo("평가를 완료하지 못했습니다. 다시 제출해 주세요.");
        body.extractingPath("$.data.failure.canResubmit").isEqualTo(true);
        body.extractingPath("$.data.result").isNull();
    }

    @Test
    void get_returns200WithNullResultFields_whenTaskIsNotLatest() {
        // Given
        loginAs("42");
        var old = new SolutionEvaluationResponse(
                9001L, 101L, "SUCCEEDED", false, new SolutionEvaluationResponse.LatestEvaluation(9004L, "SUCCEEDED"),
                null, "BINARY_SEARCH", "과거 풀이", null, null,
                ApproachSubmitResponse.SubmissionUsage.of(3), CREATED_AT, null, null, null);
        when(solutionEvaluationTaskService.get(42L, 9001L)).thenReturn(old);

        // When & Then
        var body = mvc().get().uri("/solution-evaluations/9001")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.data.isLatest").isEqualTo(false);
        body.extractingPath("$.data.latestEvaluation.evaluationTaskId").isEqualTo(9004);
        body.extractingPath("$.data.submissionId").isNull();
        body.extractingPath("$.data.result").isNull();
        body.extractingPath("$.data.failure").isNull();
    }

    @Test
    void get_returns404_whenTaskNotFoundOrNotOwned() {
        // Given
        loginAs("42");
        when(solutionEvaluationTaskService.get(42L, 9001L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "solution_evaluation_not_found"));

        // When & Then
        mvc().get().uri("/solution-evaluations/9001")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("solution_evaluation_not_found");
    }

    @Test
    void get_returns400_whenTaskIdNotNumeric() {
        loginAs("42");

        mvc().get().uri("/solution-evaluations/abc")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_evaluation_task_id");
    }
}
