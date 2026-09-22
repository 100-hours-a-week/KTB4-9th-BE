package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.AiBattleCreateRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleCaseResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleParticipationResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyBattleService {

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleCaseRepository battleCaseRepository;

    private final UserRepository userRepository;

    private final BattleParticipationRepository battleParticipationRepository;

    @Transactional
    public Long createDailyBattle(AiBattleCreateRequestDto request){

        LocalDate date = LocalDate.now();

        DailyBattle saving_problem = new DailyBattle(
                date,
                request.category(),
                request.problemTitle(),
                request.problemDescription()
        );

        dailyBattleRepository.save(saving_problem);

        for (int i = 0 ; i < request.cases().size() ; i++){
            AiBattleCreateRequestDto.AiBattleCaseCreateRequestDto caseRequest = request.cases().get(i);

            BattleCase battleCase = new BattleCase(
                    saving_problem,
                    caseRequest.input(),
                    caseRequest.output(),
                    i + 1       // ← 1, 2, 3 자동 부여
            );

            battleCaseRepository.save(battleCase);

        }

        return saving_problem.getBattleId();
    }

    @Transactional
    public BattleParticipationResponseDto battleParticipation(Long battle_id, Long user_id){

        // user 정보 있는지 확인
        //TODO: 유저 없을때 예외처리
        User user = userRepository.findById(user_id).get();

        // 배틀 정보 있는지 확인
        //TODO: 배틀문제 없을때 예외처리
        DailyBattle dailyBattle = dailyBattleRepository.findById(battle_id).get();

        // 배틀 참여 상태 엔티티 생성
        BattleParticipation battle_participation = new BattleParticipation(
                dailyBattle,
                user
        );

        battleParticipationRepository.save(battle_participation);

        // 배틀 정보 불러오기 (제목, 본문)
        String title = dailyBattle.getTitle();
        String content = dailyBattle.getContent();

        // 배틀 케이스 정보 불러오기 (인풋, 순서)
        List<BattleCase> battle_cases_list = battleCaseRepository.findByDailyBattle_BattleId(battle_id);

        // 배틀 케이스 응답 dto 만들기
        List<BattleCaseResponseDto>  battle_case_response_list = new ArrayList<>();

        for (BattleCase battle_case : battle_cases_list){
            String input = battle_case.getInput();
            Integer display_order = battle_case.getDisplayOrder();

            BattleCaseResponseDto battle_cases = new BattleCaseResponseDto(input, display_order);
            battle_case_response_list.add(battle_cases);
        }

        //시작시간
        OffsetDateTime startedAt = battle_participation
                .getStartedAt()
                .atOffset(ZoneOffset.ofHours(9));

        // 남은 시간 계산
        LocalDateTime battleEndTime = dailyBattle.getBattleDate()
                .atTime(12, 10);

        LocalDateTime now = LocalDateTime.now();

        Long remained_time_second = Math.max(
                0L,
                Duration.between(now, battleEndTime).getSeconds()
        );

        // 배틀 참여 응답 dto 만들기
        BattleParticipationResponseDto battleParticipationResponseDto = new BattleParticipationResponseDto(
                dailyBattle.getCategory(),
                battle_id,
                user_id,
                dailyBattle.getBattleDate(),
                title,
                content,
                battle_participation.getParticipationStatus(),
                startedAt,
                remained_time_second,
                battle_case_response_list
        );

        //반환
        return battleParticipationResponseDto;
    }

}
