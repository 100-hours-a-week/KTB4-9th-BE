package com.cosmos.cosmos_backend.notification.controller;

import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.notification.dto.response.NotificationListResponse;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "알림", description = "평가 완료 알림 API")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "문제별 최신 평가에 연결된 알림만 읽음 여부와 상관없이 최신순 10개까지, 같은 조건의 안 읽은 알림 전체 개수와 진행 중인 평가 여부와 함께 반환합니다.")
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 조회는 Service에게 맡기고 message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("notification_list_success",
                notificationService.getNotifications(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "알림을 읽음 처리합니다. 이미 읽은 알림이어도 200을 반환합니다.")
    public ResponseEntity<ApiResponse<Void>> read(
            @Parameter(description = "알림 ID", required = true, example = "12") @PathVariable Long notificationId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. 로그인한 사용자 id를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 읽음 처리는 Service에게 맡김 (알림이 없거나 남의 것이면 404 예외)
        notificationService.markRead(userId, notificationId);

        // 3. data 없이 message만 담아 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("notification_read_success"));
    }
}
