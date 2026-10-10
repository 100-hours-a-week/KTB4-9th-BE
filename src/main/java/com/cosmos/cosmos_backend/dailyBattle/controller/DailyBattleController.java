package com.cosmos.cosmos_backend.dailyBattle.controller;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.AiBattleCreateRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleParticipationResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/daily-battles")
@RequiredArgsConstructor
@Tag(name = "데일리 배틀", description = "데일리 배틀 문제 생성 및 참여 API")
public class DailyBattleController {

    private final DailyBattleService  dailyBattleService;

    private final Clock clock;

    // 배틀 문제 저장
    @PostMapping("/problem")
    @Operation(summary = "데일리 배틀 문제 생성", description = "AI가 생성한 배틀 문제와 테스트 케이스를 저장합니다.")
    public ResponseEntity<ApiResponse<Void>> createDailyBattle(
            @Valid @RequestBody AiBattleCreateRequestDto request
    ) {

        Long battleId = dailyBattleService.createDailyBattle(request);

        return ResponseEntity
                .created(URI.create("/daily-battles/" + battleId))
                .body(ApiResponse.of(
                        "daily_battle_problem_creation_success"
                ));
    }

    //배틀 참여
    @PostMapping("/{battle_id}/participations")
    @Operation(summary = "데일리 배틀 참여", description = "배틀에 참여하고 문제 정보, 테스트 케이스와 남은 시간을 반환합니다.")
    public ResponseEntity<ApiResponse<BattleParticipationResponseDto>> battleParticipation(
            @Parameter(description = "배틀 ID", required = true, example = "1") @PathVariable("battle_id") @Positive Long battleId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt userInfo
    ){

        // 12시 ~ 12:10 사이가 아니면 참여 불가
        LocalTime now = LocalTime.now(clock);

        LocalTime start = LocalTime.of(12, 0);
        LocalTime end = LocalTime.of(12, 10);

        if (now.isBefore(start) || !now.isBefore(end)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Not_Battle_Time");
        }

        Long userId = Long.parseLong(Objects.requireNonNull(userInfo.getSubject()));

        BattleParticipationResponseDto battleParticipationResponse = dailyBattleService.battleParticipation(battleId, userId);

        return ResponseEntity.ok(
                ApiResponse.of(
                        "battle_participation_resumed",
                        battleParticipationResponse
                )
        );
    }

    // 배틀 제출





}
