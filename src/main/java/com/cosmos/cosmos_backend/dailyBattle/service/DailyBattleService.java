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
    public BattleParticipationResponseDto battleParticipation(Long battleId, Long userId){

        // user 정보 있는지 확인
        //TODO: 유저 없을때 예외처리
        User user = userRepository.findById(userId).get();

        // 배틀 정보 있는지 확인
        //TODO: 배틀문제 없을때 예외처리
        DailyBattle dailyBattle = dailyBattleRepository.findById(battleId).get();

        // 배틀 참여 상태 엔티티 생성
        BattleParticipation battleParticipation = new BattleParticipation(
                dailyBattle,
                user
        );

        battleParticipationRepository.save(battleParticipation);

        // 배틀 정보 불러오기 (제목, 본문)
        String title = dailyBattle.getTitle();
        String content = dailyBattle.getContent();

        // 배틀 케이스 정보 불러오기 (인풋, 순서)
        List<BattleCase> battleCaseList = battleCaseRepository.findByDailyBattle_BattleId(battleId);

        // 배틀 케이스 응답 dto 만들기
        List<BattleCaseResponseDto>  battleCaseResponseList = new ArrayList<>();

        for (BattleCase battleCase : battleCaseList){
            String input = battleCase.getInput();
            Integer displayOrder = battleCase.getDisplayOrder();

            BattleCaseResponseDto battleCases = new BattleCaseResponseDto(input, displayOrder);
            battleCaseResponseList.add(battleCases);
        }

        //시작시간
        OffsetDateTime startedAt = battleParticipation
                .getStartedAt()
                .atOffset(ZoneOffset.ofHours(9));

        // 남은 시간 계산
        LocalDateTime battleEndTime = dailyBattle.getBattleDate()
                .atTime(12, 10);

        LocalDateTime now = LocalDateTime.now();

        Long remainedTimeSecond = Math.max(
                0L,
                Duration.between(now, battleEndTime).getSeconds()
        );

        // 배틀 참여 응답 dto 만들기
        BattleParticipationResponseDto battleParticipationResponseDto = new BattleParticipationResponseDto(
                dailyBattle.getCategory(),
                battleId,
                userId,
                dailyBattle.getBattleDate(),
                title,
                content,
                battleParticipation.getParticipationStatus(),
                startedAt,
                remainedTimeSecond,
                battleCaseResponseList
        );

        //반환
        return battleParticipationResponseDto;
    }

}
