package com.cosmos.cosmos_backend.problem.controller;

import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.dto.response.HintResponse;
import com.cosmos.cosmos_backend.problem.service.HintService;
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
class HintControllerTest {

    @Mock
    private HintService hintService;

    private MockMvcTester mvc() {
        HintController controller = new HintController(hintService);
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
    void commentHint_returns200WithSnakeCaseBody() {
        // Given
        loginAs("7");
        when(hintService.getHint(7L, 1L, "PYTHON", HintType.COMMENT))
                .thenReturn(HintResponse.of(1L, HintType.COMMENT, 1, "# 힌트"));

        // When & Then
        var body = mvc().post().uri("/problems/1/hints/comment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\"}")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("comment_hint_retrieval_success");
        body.extractingPath("$.data.problem_id").isEqualTo(1);
        body.extractingPath("$.data.hint_type").isEqualTo("COMMENT");
        body.extractingPath("$.data.hint_stage").isEqualTo(1);
        body.extractingPath("$.data.content").isEqualTo("# 힌트");
    }

    @Test
    void answerHint_returns200WithAnswerType() {
        // Given
        loginAs("7");
        when(hintService.getHint(7L, 1L, "JAVA", HintType.SOLUTION))
                .thenReturn(HintResponse.of(1L, HintType.SOLUTION, 2, "class A {}"));

        // When & Then
        var body = mvc().post().uri("/problems/1/hints/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"JAVA\"}")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("answer_hint_retrieval_success");
        body.extractingPath("$.data.hint_type").isEqualTo("ANSWER");
        body.extractingPath("$.data.hint_stage").isEqualTo(2);
    }

    @Test
    void hint_returns400_whenLanguageMissing() {
        loginAs("7");

        mvc().post().uri("/problems/1/hints/comment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("language_is_required");
    }

    @Test
    void hint_returns400_whenProblemIdNotNumeric() {
        loginAs("7");

        mvc().post().uri("/problems/abc/hints/comment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_problem_id");
    }

    @Test
    void answerHint_returns409_whenCommentHintNotUsed() {
        // Given
        loginAs("7");
        when(hintService.getHint(7L, 1L, "PYTHON", HintType.SOLUTION))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "comment_hint_required"));

        // When & Then
        mvc().post().uri("/problems/1/hints/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\"}")
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("comment_hint_required");
    }

    @Test
    void hint_returns404_whenServiceThrowsNotFound() {
        // Given
        loginAs("7");
        when(hintService.getHint(7L, 1L, "PYTHON", HintType.SOLUTION))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "answer_hint_not_found"));

        // When & Then
        mvc().post().uri("/problems/1/hints/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"PYTHON\"}")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("answer_hint_not_found");
    }
}
