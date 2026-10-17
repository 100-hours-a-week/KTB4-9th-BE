package com.cosmos.cosmos_backend.battlealarm.controller;

import com.cosmos.cosmos_backend.battlealarm.dto.request.BattleAlarmDismissRequest;
import com.cosmos.cosmos_backend.battlealarm.dto.response.BattleAlarmDismissResponse;
import com.cosmos.cosmos_backend.battlealarm.dto.response.BattleAlarmStatusResponse;
import com.cosmos.cosmos_backend.battlealarm.service.BattleAlarmService;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/daily-battles")
@RequiredArgsConstructor
@Tag(name = "배틀 알림", description = "배틀 알림 모달의 상태 확인과 닫기 API")
public class BattleAlarmController {

    private final BattleAlarmService battleAlarmService;

    @GetMapping("/alarm")
    @Operation(summary = "배틀 알림 상태 확인", description = "서버 시각 기준으로 배틀 알림 모달의 표시 상태를 반환합니다.")
    public ResponseEntity<ApiResponse<BattleAlarmStatusResponse>> getStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. JWT에서 사용자 id를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 상태 계산은 Service에게 맡기고, message + data 형식으로 감싸서 200으로 반환
        BattleAlarmStatusResponse response = battleAlarmService.getStatus(userId);
        return ResponseEntity.ok(ApiResponse.of("battle_alarm_status_retrieval_success", response));
    }

    @PatchMapping("/{battle_id}/alarm")
    @Operation(summary = "배틀 알림 닫기", description = "모달에서 닫기를 누른 것을 저장합니다. 이미 닫은 알림이어도 200을 반환합니다.")
    public ResponseEntity<ApiResponse<BattleAlarmDismissResponse>> dismiss(
            @Parameter(description = "배틀 ID", required = true, example = "3") @PathVariable("battle_id") Long battleId,
            @Valid @RequestBody BattleAlarmDismissRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. JWT에서 사용자 id를 꺼냄 (요청 본문 검사는 @Valid가 이미 끝냄)
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 닫기 저장은 Service에게 맡기고, message + data 형식으로 감싸서 200으로 반환
        battleAlarmService.dismiss(userId, battleId);
        return ResponseEntity.ok(ApiResponse.of("battle_alarm_dismissed", new BattleAlarmDismissResponse(battleId, true)));
    }
}
