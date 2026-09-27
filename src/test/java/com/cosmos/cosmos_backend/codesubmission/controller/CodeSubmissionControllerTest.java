package com.cosmos.cosmos_backend.codesubmission.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.codesubmission.domain.CodeSubmission;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.codesubmission.dto.response.CodeSubmitResponse;
import com.cosmos.cosmos_backend.codesubmission.service.CodeSubmissionService;
import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
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
class CodeSubmissionControllerTest {

    @Mock
    private CodeSubmissionService codeSubmissionService;

    private MockMvcTester mvc() {
        CodeSubmissionController controller = new CodeSubmissionController(codeSubmissionService);
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
    void submit_returns200WithSnakeCaseBody() {
        // Given
        loginAs("7");
        LocalDateTime received = LocalDateTime.of(2026, 9, 4, 15, 30, 0);
        CodeSubmission submission = new CodeSubmission(7L, 15L, Language.JAVA, "class Main {}", received, JudgingResult.CORRECT, 10, 10);
        when(codeSubmissionService.submit(7L, 15L, "JAVA", "class Main {}")).thenReturn(submission);

        // When & Then
        var body = mvc().post().uri("/problems/15/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"JAVA\",\"source_code\":\"class Main {}\"}")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("code_submission_completed");
        body.extractingPath("$.data.problem_id").isEqualTo(15);
        body.extractingPath("$.data.language").isEqualTo("JAVA");
        body.extractingPath("$.data.judging_status").isEqualTo("COMPLETED");
        body.extractingPath("$.data.judging_result").isEqualTo("CORRECT");
        body.extractingPath("$.data.passed_test_count").isEqualTo(10);
        body.extractingPath("$.data.total_test_count").isEqualTo(10);
        body.extractingPath("$.data.submitted_count").isEqualTo(1);
        body.extractingPath("$.data.submitted_at").isEqualTo("2026-09-04T15:30:00+09:00");
        body.extractingPath("$.data.judged_at").isNotNull();
    }

    @Test
    void submit_doesNotRequireIdempotencyKeyHeader() {
        // Given
        loginAs("7");
        CodeSubmission submission = new CodeSubmission(7L, 1L, Language.PYTHON, "a", LocalDateTime.now(), JudgingResult.WRONG_ANSWER, 0, 1);
        when(codeSubmissionService.submit(7L, 1L, "PYTHON", "a")).thenReturn(submission);

        // When & Then: 헤더 없이도 200, 헤더가 있어도 200
        mvc().post().uri("/problems/1/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\",\"source_code\":\"a\"}")
                .assertThat().hasStatusOk();
        mvc().post().uri("/problems/1/code-submissions")
                .header("Idempotency-Key", "abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\",\"source_code\":\"a\"}")
                .assertThat().hasStatusOk();
    }

    @Test
    void submit_returns400_whenSourceCodeMissing() {
        loginAs("7");

        mvc().post().uri("/problems/1/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("source_code_is_required");
        verifyNoInteractions(codeSubmissionService);
    }

    @Test
    void submit_returns400_whenLanguageMissing() {
        loginAs("7");

        mvc().post().uri("/problems/1/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source_code\":\"a\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("language_is_required");
        verifyNoInteractions(codeSubmissionService);
    }

    @Test
    void submit_returns400_whenProblemIdNotNumeric() {
        loginAs("7");

        mvc().post().uri("/problems/abc/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\",\"source_code\":\"a\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_problem_id");
        verifyNoInteractions(codeSubmissionService);
    }

    @Test
    void submit_returns429WithSnakeCaseData_whenLimitExceeded() {
        // Given
        loginAs("7");
        when(codeSubmissionService.submit(any(), any(), any(), any())).thenThrow(
                new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "submission_limit_exceeded",
                        new CodeSubmitResponse.SubmissionLimitExceededData(5, 5)));

        // When & Then
        var body = mvc().post().uri("/problems/1/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\",\"source_code\":\"a\"}")
                .assertThat()
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson();
        body.extractingPath("$.message").isEqualTo("submission_limit_exceeded");
        body.extractingPath("$.data.submitted_count").isEqualTo(5);
        body.extractingPath("$.data.submission_limit").isEqualTo(5);
    }

    @Test
    void submit_returns503_whenJudgeServerUnavailable() {
        // Given
        loginAs("7");
        when(codeSubmissionService.submit(any(), any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable"));

        // When & Then
        mvc().post().uri("/problems/1/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\",\"source_code\":\"a\"}")
                .assertThat()
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("judge_server_unavailable");
    }

    @Test
    void submit_returns404_whenServiceThrowsNotFound() {
        // Given
        loginAs("7");
        when(codeSubmissionService.submit(any(), any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // When & Then
        mvc().post().uri("/problems/999/code-submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\",\"source_code\":\"a\"}")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_not_found");
    }
}
