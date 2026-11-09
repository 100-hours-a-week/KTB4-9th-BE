package com.cosmos.cosmos_backend.savedproblem.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblemStatus;
import com.cosmos.cosmos_backend.savedproblem.dto.response.SavedProblemResponse;
import com.cosmos.cosmos_backend.savedproblem.service.SavedProblemService;
import java.time.LocalDateTime;
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
class SavedProblemControllerTest {

    @Mock
    private SavedProblemService savedProblemService;

    private MockMvcTester mvc() {
        SavedProblemController controller = new SavedProblemController(savedProblemService);
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
    void save_returns201_whenNewlySaved() {
        // Given
        loginAs("7");
        when(savedProblemService.save(7L, 1L, false)).thenReturn(true);

        // When & Then: 본문이 없으면 카테고리를 몰랐던 것으로 저장
        mvc().post().uri("/problems/1/saved")
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_save_success");
    }

    @Test
    void save_passesCategoryKnown_whenBodyHasIt() {
        // Given
        loginAs("7");
        when(savedProblemService.save(7L, 1L, true)).thenReturn(true);

        // When & Then
        mvc().post().uri("/problems/1/saved")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryKnown\":true}")
                .assertThat()
                .hasStatus(HttpStatus.CREATED);
        verify(savedProblemService).save(7L, 1L, true);
    }

    @Test
    void save_returns200_whenAlreadySaved() {
        // Given
        loginAs("7");
        when(savedProblemService.save(7L, 1L, false)).thenReturn(false);

        // When & Then
        mvc().post().uri("/problems/1/saved")
                .assertThat()
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void save_returns404_whenProblemMissing() {
        // Given
        loginAs("7");
        when(savedProblemService.save(7L, 999L, false))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // When & Then
        mvc().post().uri("/problems/999/saved")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_not_found");
    }

    @Test
    void save_returns400_whenProblemIdNotNumeric() {
        loginAs("7");
        mvc().post().uri("/problems/abc/saved")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_problem_id");
    }

    @Test
    void unsave_returns204() {
        // Given
        loginAs("7");

        // When & Then
        mvc().delete().uri("/problems/1/saved")
                .assertThat()
                .hasStatus(HttpStatus.NO_CONTENT);
        verify(savedProblemService).unsave(7L, 1L);
    }

    @Test
    void getSavedProblems_returns200WithProblems() {
        // Given: level 없이 요청하면 서비스에 null이 넘어감
        loginAs("7");
        when(savedProblemService.getSavedProblems(7L, null)).thenReturn(List.of(
                new SavedProblemResponse(1L, "두 수의 합", Difficulty.LV1, Category.ARRAY,
                        LocalDateTime.of(2026, 10, 8, 12, 0), SavedProblemStatus.CORRECT, "투 포인터로 푼다")
        ));

        // When & Then
        var body = mvc().get().uri("/saved-problems")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("saved_problems_retrieval_success");
        body.extractingPath("$.data.problems[0].problemId").isEqualTo(1);
        body.extractingPath("$.data.problems[0].title").isEqualTo("두 수의 합");
        body.extractingPath("$.data.problems[0].status").isEqualTo("CORRECT");
        body.extractingPath("$.data.problems[0].solution").isEqualTo("투 포인터로 푼다");
    }

    @Test
    void getSavedProblems_passesLevelFilter() {
        // Given
        loginAs("7");
        when(savedProblemService.getSavedProblems(7L, Difficulty.LV2)).thenReturn(List.of());

        // When & Then
        mvc().get().uri("/saved-problems?level=LV2")
                .assertThat()
                .hasStatusOk();
        verify(savedProblemService).getSavedProblems(7L, Difficulty.LV2);
    }

    @Test
    void getSavedProblems_returns400_whenLevelInvalid() {
        loginAs("7");
        mvc().get().uri("/saved-problems?level=LV9")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_request_parameter");
    }
}
