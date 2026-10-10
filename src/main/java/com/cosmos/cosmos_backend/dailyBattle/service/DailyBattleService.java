package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyBattleService {

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleCaseRepository battleCaseRepository;

    private final UserRepository userRepository;

    private final BattleParticipationRepository battleParticipationRepository;

    private final Clock clock;

    @Transactional
    public Long createDailyBattle(AiBattleCreateRequestDto request){

        LocalDate date = LocalDate.now(clock);

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
        // 유저 없을때 예외처리
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User_Not_Found"));

        // 이미 배틀에 참여중인지 확인
        // 참여중이라면 새 엔티티 생성 없음, 새 참여일경우에만 새 엔티티 생성
        BattleParticipation battleParticipation = battleParticipationRepository.findByUser_Id(user.getId())
                .orElse(null);

        if(battleParticipation == null){
            battleParticipation = new BattleParticipation(
                    battleId,
                    user
            );

            battleParticipationRepository.save(battleParticipation);

        }

        // 이미 배틀 참여 완료 했으면 참여 불가
        if(battleParticipation.getParticipationStatus() !=  ParticipationStatus.IN_PROGRESS){
            throw new BusinessException(HttpStatus.CONFLICT, "Battle_already_participated");
        }

        // 배틀 정보 불러오기 (제목, 본문)
        DailyBattle battleInfo = dailyBattleRepository.findById(battleId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_Not_Found"));

        String title = battleInfo.getTitle();
        String content = battleInfo.getContent();

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

        // 남은 시간 계산 (마감이 KST 기준 시각이라 현재 시각도 KST로 계산)
        LocalDateTime battleEndTime = battleInfo.getBattleDate()
                .atTime(12, 10);

        LocalDateTime now = LocalDateTime.now(clock);

        Long remainedTimeSecond = Math.max(
                0L,
                Duration.between(now, battleEndTime).getSeconds()
        );

        // 배틀 참여 응답 dto 만들기
        BattleParticipationResponseDto battleParticipationResponseDto = new BattleParticipationResponseDto(
                battleInfo.getCategory(),
                battleId,
                userId,
                battleInfo.getBattleDate(),
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
