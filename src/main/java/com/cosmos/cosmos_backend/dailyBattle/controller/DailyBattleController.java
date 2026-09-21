package com.cosmos.cosmos_backend.dailyBattle.controller;

import com.cosmos.cosmos_backend.dailyBattle.dto.request.AiBattleCreateRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleParticipationResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/daily-battles")
@RequiredArgsConstructor
public class DailyBattleController {

    private final DailyBattleService  dailyBattleService;

    // 배틀 문제 저장
    @PostMapping("/problem")
    public ResponseEntity<Void> createDailyBattle(
            @Valid @RequestBody AiBattleCreateRequestDto request
    ) {

        Long battleId = dailyBattleService.createDailyBattle(request);

        return ResponseEntity
                .created(URI.create("/daily-battles/" + battleId))
                .build();
    }

    //배틀 참여
    @PostMapping("/{battle_id}/participations")
    public BattleParticipationResponseDto battleParticipation(
            @PathVariable @Positive Long battle_id,
            @AuthenticationPrincipal Jwt jwt
    ){

        Long user_id = Long.parseLong(jwt.getSubject());

        BattleParticipationResponseDto battleParticipationResponse = dailyBattleService.battleParticipation(battle_id, user_id);

        return battleParticipationResponse;
    }

}
