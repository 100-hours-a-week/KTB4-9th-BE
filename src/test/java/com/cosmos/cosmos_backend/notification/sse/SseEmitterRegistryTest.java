package com.cosmos.cosmos_backend.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cosmos.cosmos_backend.notification.controller.SseController;
import com.cosmos.cosmos_backend.notification.event.EvaluationCompletedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
        mvc = MockMvcBuilders.standaloneSetup(new SseController(registry))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        registry.stop();
        SecurityContextHolder.clearContext();
    }

    // 사용자가 /events로 연결을 연 상태를 만든다.
    private MvcResult connect(String userId) throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(userId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        return mvc.perform(MockMvcRequestBuilders.get("/events")).andReturn();
    }

    // 응답에 문구가 나타날 때까지 최대 2초 기다린다.
    private boolean contentContains(MvcResult result, String text) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (result.getResponse().getContentAsString().contains(text)) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private EvaluationCompletedEvent event(Long userId) {
        return new EvaluationCompletedEvent("evt-1", userId, 9001L, "SUCCEEDED", 7001L);
    }

    @Test
    void register_sendsConnectedEvent() throws Exception {
        // When
        MvcResult result = connect("42");

        // Then
        assertThat(contentContains(result, "event:connected")).isTrue();
        assertThat(result.getResponse().getContentAsString()).contains("sse_connected");
    }

    @Test
    void dispatch_sendsEventToConnectedUser() throws Exception {
        // Given
        MvcResult result = connect("42");

        // When
        registry.dispatch(event(42L));

        // Then
        assertThat(contentContains(result, "event:evaluation-completed")).isTrue();
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("id:evt-1");
        assertThat(content).contains("\"evaluationTaskId\":9001");
        assertThat(content).contains("\"status\":\"SUCCEEDED\"");
        assertThat(content).contains("\"notificationId\":7001");
        assertThat(content).doesNotContain("userId");
    }

    @Test
    void dispatch_sendsOnlyToTargetUser() throws Exception {
        // Given
        MvcResult target = connect("42");
        MvcResult other = connect("43");

        // When
        registry.dispatch(event(42L));

        // Then
        assertThat(contentContains(target, "event:evaluation-completed")).isTrue();
        assertThat(other.getResponse().getContentAsString()).doesNotContain("evaluation-completed");
    }

    @Test
    void dispatch_sendsToEveryTabOfUser() throws Exception {
        // Given
        MvcResult firstTab = connect("42");
        MvcResult secondTab = connect("42");

        // When
        registry.dispatch(event(42L));

        // Then
        assertThat(contentContains(firstTab, "event:evaluation-completed")).isTrue();
        assertThat(contentContains(secondTab, "event:evaluation-completed")).isTrue();
    }

    @Test
    void dispatch_doesNothing_whenUserHasNoConnection() {
        assertThatCode(() -> registry.dispatch(event(99L))).doesNotThrowAnyException();
    }
}
