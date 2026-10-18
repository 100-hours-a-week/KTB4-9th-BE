package com.cosmos.cosmos_backend.dailyBattle.controller;

import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.AiBattleCreateRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.DailyBattleSubmissionRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleDropOutResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleParticipationResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleResultResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleSubmissionResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.scheduler.BattleWorkerScheduler;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleResultService;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@RestController
@RequestMapping("/daily-battles")
@RequiredArgsConstructor
@Tag(name = "데일리 배틀", description = "데일리 배틀 문제 생성 및 참여 API")
public class DailyBattleController {

    private final DailyBattleService  dailyBattleService;

    private final BattleResultService battleResultService;

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
            @Parameter(description = "배틀 ID", required = true, example = "1")
            @PathVariable("battle_id") @Positive Long battleId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt userInfo
    ){

        // 사용자 정보 추출
        Long userId = Long.parseLong(userInfo.getSubject());

        BattleParticipationResponseDto battleParticipationResponse = dailyBattleService.battleParticipation(battleId, userId);

        return ResponseEntity.ok(
                ApiResponse.of(
                        "battle_participation_resumed",
                        battleParticipationResponse
                )
        );
    }

    // 배틀 제출
    @PostMapping("/{battle_id}/submissions")
    public ResponseEntity<BattleSubmissionResponseDto> battleSubmission(
            @Valid @RequestBody DailyBattleSubmissionRequestDto battleSubmissionRequest,
            @PathVariable("battle_id") @Positive Long battleId,
            @AuthenticationPrincipal Jwt userInfo,
            @RequestAttribute("battleReceivedAt") Instant submittedAt
            ){

        Long userId = Long.valueOf(userInfo.getSubject());

        BattleSubmissionResponseDto battleSubmissionResponse = dailyBattleService.battleSubmission(battleSubmissionRequest, battleId, userId, submittedAt);

        // 202 반환
        return ResponseEntity.accepted().body(ApiResponse.of(
                "battle_submission_resumed",
                battleSubmissionResponse).data()

        );
    }

    // 배틀 중도 포기
    @PatchMapping("/{battle_id}/participations")
    public ResponseEntity<ApiResponse<BattleDropOutResponseDto>> dropOutBattle(
            @AuthenticationPrincipal Jwt userInfo,
            @PathVariable("battle_id") Long battleId
    ) {
        Long userId = Long.parseLong(userInfo.getSubject());

        BattleDropOutResponseDto battleResultResponse = dailyBattleService.dropOutBattle(battleId, userId);

        return ResponseEntity.ok(
                ApiResponse.of(
                        "battle_drop_out",
                        battleResultResponse
                )
        );
    }


    // 배틀 결과 조회
    @GetMapping("/{battle_id}/results")
    public ResponseEntity<ApiResponse<BattleResultResponseDto>> battleResult(
            @AuthenticationPrincipal Jwt userInfo,
            @Positive @NotNull @PathVariable("battle_id") Long battleId
    ) {

        Long userId = Long.parseLong(userInfo.getSubject());

        BattleResultResponseDto battleResultResponse = battleResultService.getBattleResult(battleId, userId);

        // 결과 공개 확정 안 되었다면 202로
        if (battleResultResponse == null) {
            return ResponseEntity.accepted().body(ApiResponse.of(
                    "battle_results_not_retrieved_yet",
                    null)
            );
        }

        // 결과 확정 되었으면 200으로 응답 반환
        return ResponseEntity.ok(
                ApiResponse.of(
                        "battle_results_retrieved",
                        battleResultResponse
                )
        );
    }

}
