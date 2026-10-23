package com.cosmos.cosmos_backend.notification.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import com.cosmos.cosmos_backend.notification.domain.Notification;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import com.cosmos.cosmos_backend.notification.dto.response.NotificationListResponse;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 18, 3, 30, 6);

    @Mock
    private NotificationService notificationService;

    private MockMvcTester mvc() {
        NotificationController controller = new NotificationController(notificationService);
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

    private Notification notification(Long id, NotificationType type, Long taskId, Long problemId, String message) {
        Notification notification = new Notification(42L, type, problemId, taskId, message, CREATED_AT);
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @Test
    void list_returns200WithNotificationsAndUnreadCount() {
        // Given
        loginAs("42");
        Notification done = notification(12L, NotificationType.NL_EVALUATION_DONE, 9001L, 101L, "'두 수의 합' 평가가 완료됐어요.");
        Notification failed = notification(13L, NotificationType.NL_EVALUATION_FAILED, 9002L, 102L,
                "'괄호 검사' 평가를 완료하지 못했어요. 다시 제출해 주세요.");
        failed.markRead(CREATED_AT.plusSeconds(10));
        when(notificationService.getNotifications(42L)).thenReturn(NotificationListResponse.of(1, false, List.of(done, failed)));

        // When & Then
        var body = mvc().get().uri("/notifications")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("notification_list_success");
        body.extractingPath("$.data.unreadCount").isEqualTo(1);
        body.extractingPath("$.data.notifications[0].id").isEqualTo(12);
        body.extractingPath("$.data.notifications[0].type").isEqualTo("NL_EVALUATION_DONE");
        body.extractingPath("$.data.notifications[0].evaluationTaskId").isEqualTo(9001);
        body.extractingPath("$.data.notifications[0].problemId").isEqualTo(101);
        body.extractingPath("$.data.notifications[0].message").isEqualTo("'두 수의 합' 평가가 완료됐어요.");
        body.extractingPath("$.data.notifications[0].isRead").isEqualTo(false);
        body.extractingPath("$.data.notifications[0].createdAt").isEqualTo("2026-10-18T03:30:06Z");
        body.extractingPath("$.data.notifications[1].type").isEqualTo("NL_EVALUATION_FAILED");
        body.extractingPath("$.data.notifications[1].isRead").isEqualTo(true);

        verify(notificationService).getNotifications(42L);
    }

    @Test
    void list_returns200WithEmptyList_whenNoNotifications() {
        // Given
        loginAs("42");
        when(notificationService.getNotifications(42L)).thenReturn(NotificationListResponse.of(0, false, List.of()));

        // When & Then
        var body = mvc().get().uri("/notifications")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.data.unreadCount").isEqualTo(0);
        body.extractingPath("$.data.notifications").asArray().isEmpty();
    }

    @Test
    void read_returns200WithNullData() {
        // Given
        loginAs("42");

        // When & Then
        var body = mvc().patch().uri("/notifications/12/read")
                .assertThat()
                .hasStatusOk()
                .bodyJson();

        body.extractingPath("$.message").isEqualTo("notification_read_success");
        body.extractingPath("$.data").isNull();
        verify(notificationService).markRead(42L, 12L);
    }

    @Test
    void read_returns404_whenNotificationMissingOrNotMine() {
        // Given
        loginAs("42");
        doThrow(new BusinessException(HttpStatus.NOT_FOUND, "notification_not_found"))
                .when(notificationService).markRead(42L, 12L);

        // When & Then
        mvc().patch().uri("/notifications/12/read")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("notification_not_found");
    }

    @Test
    void read_returns400_whenNotificationIdNotNumeric() {
        // Given
        loginAs("42");

        // When & Then
        mvc().patch().uri("/notifications/abc/read")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_request_parameter");
    }
}
