package com.cosmos.cosmos_backend.notification.controller;

import com.cosmos.cosmos_backend.notification.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@Tag(name = "SSE", description = "실시간 이벤트 구독 API")
public class SseController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "이벤트 구독", description = "평가 완료 같은 실시간 이벤트를 SSE로 받습니다. 연결 직후 connected 이벤트가 오고, 15초마다 heartbeat가 옵니다.")
    public ResponseEntity<SseEmitter> subscribe(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        // 1. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 레지스트리에 연결을 등록 (connected 이벤트도 이때 전송됨)
        SseEmitter emitter = registry.register(userId);

        // 3. 프록시가 응답을 모아두지 않도록 헤더를 붙여 반환
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}
