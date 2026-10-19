package com.cosmos.cosmos_backend.battlealarm.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.battlealarm.dto.response.BattleAlarmStatusResponse;
import com.cosmos.cosmos_backend.battlealarm.service.BattleAlarmService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
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
class BattleAlarmControllerTest {

    @Mock
    private BattleAlarmService battleAlarmService;

    private MockMvcTester mvc() {
        BattleAlarmController controller = new BattleAlarmController(battleAlarmService);
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
    void getStatus_returns200WithSnakeCaseFields() {
        // Given
        loginAs("7");
        when(battleAlarmService.getStatus(7L)).thenReturn(BattleAlarmStatusResponse.open(
                OffsetDateTime.parse("2026-10-13T12:50:01+09:00"), 3L, false, false,
                OffsetDateTime.parse("2026-10-13T13:00:00+09:00")
        ));

        // When & Then: 필드 이름이 snake_case이고 시각에 +09:00이 붙어서 나감
        var body = mvc().get().uri("/daily-battles/alarm")
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.message").isEqualTo("battle_alarm_status_retrieval_success");
        body.extractingPath("$.data.status").isEqualTo("OPEN");
        body.extractingPath("$.data.battle_id").isEqualTo(3);
        body.extractingPath("$.data.participated").isEqualTo(false);
        body.extractingPath("$.data.dismissed").isEqualTo(false);
        body.extractingPath("$.data.server_time").asString().isEqualTo("2026-10-13T12:50:01+09:00");
        body.extractingPath("$.data.closes_at").asString().isEqualTo("2026-10-13T13:00:00+09:00");
    }

    @Test
    void dismiss_returns200_andCallsServiceWithUserAndBattle() {
        // Given
        loginAs("7");

        // When & Then
        var body = mvc().patch().uri("/daily-battles/3/alarm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dismissed\": true}")
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.message").isEqualTo("battle_alarm_dismissed");
        body.extractingPath("$.data.battle_id").isEqualTo(3);
        body.extractingPath("$.data.dismissed").isEqualTo(true);
        verify(battleAlarmService).dismiss(7L, 3L);
    }

    @Test
    void dismiss_returns400_whenBattleIdIsNotNumber() {
        // Given
        loginAs("7");

        // When & Then
        mvc().patch().uri("/daily-battles/abc/alarm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dismissed\": true}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_request_parameter");
    }

    @Test
    void dismiss_returns400_whenBodyIsMissing() {
        // Given
        loginAs("7");

        // When & Then
        mvc().patch().uri("/daily-battles/3/alarm")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_request_body");
    }

    @Test
    void dismiss_returns400_whenBodyIsBrokenJson() {
        // Given
        loginAs("7");

        // When & Then
        mvc().patch().uri("/daily-battles/3/alarm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_request_body");
    }

    @Test
    void dismiss_returns400_whenDismissedIsMissing() {
        // Given
        loginAs("7");

        // When & Then
        mvc().patch().uri("/daily-battles/3/alarm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("dismissed_is_required");
    }

    @Test
    void dismiss_returns400_whenDismissedIsFalse() {
        // Given
        loginAs("7");

        // When & Then
        mvc().patch().uri("/daily-battles/3/alarm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dismissed\": false}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_dismissed");
    }

    @Test
    void dismiss_returns404_whenBattleNotFound() {
        // Given: 서비스가 없는 배틀이라고 알림
        loginAs("7");
        doThrow(new BusinessException(HttpStatus.NOT_FOUND, "daily_battle_not_found"))
                .when(battleAlarmService).dismiss(7L, 3L);

        // When & Then
        mvc().patch().uri("/daily-battles/3/alarm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dismissed\": true}")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("daily_battle_not_found");
    }
}
