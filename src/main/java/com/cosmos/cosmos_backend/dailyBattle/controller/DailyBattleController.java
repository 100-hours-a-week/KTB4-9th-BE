package com.cosmos.cosmos_backend.dailyBattle.controller;

import com.cosmos.cosmos_backend.dailyBattle.dto.AiBattleCreateRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.BattleParticipationResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/daily-battles")
@RequiredArgsConstructor
public class DailyBattleController {

    private final DailyBattleService  dailyBattleService;

    // 배틀 문제 저장
    @PostMapping("/daily-battles")
    public ResponseEntity<Void> createDailyBattle(
            @RequestBody AiBattleCreateRequestDto request
    ) {

        Long battleId = dailyBattleService.createDailyBattle(request);

        return ResponseEntity
                .created(URI.create("/daily-battles/" + battleId))
                .build();
    }

    //배틀 참여
    @PostMapping("/{battle_id}/participations")
    public BattleParticipationResponseDto battleParticipation(
            @PathVariable Long battle_id
    ){

        BattleParticipationResponseDto battleParticipationResponse = dailyBattleService.battleParticipation(battle_id);

        return battleParticipationResponse;
    }

}
