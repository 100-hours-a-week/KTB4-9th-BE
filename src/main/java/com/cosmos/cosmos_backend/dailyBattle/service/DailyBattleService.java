package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.AiBattleCreateRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.DailyBattleSubmissionRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleCaseResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleDropOutResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleParticipationResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleSubmissionResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DailyBattleService {

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleCaseRepository battleCaseRepository;

    private final UserRepository userRepository;

    private final BattleParticipationRepository battleParticipationRepository;

    private final BattleSubmissionRepository battleSubmissionRepository;

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

        // 배틀 정보 불러오기
        DailyBattle battleInfo = battleValidCheck(battleId);

        // 참여 가능 날짜, 시간 검증
        battleTimeCheck(clock.instant());
        battleDateCheck(clock.instant(), battleInfo.getBattleDate());

        // 기존 참여 정보 조회
        BattleParticipation participationInfo =
                battleParticipationCheck(battleId, user.getId());

        // 최초 참여라면 새 참여 기록 생성, 기존 참여 기록이 있다면 그대로 사용
        if (participationInfo == null) {
            participationInfo = new BattleParticipation(battleId, user);
            participationInfo = battleParticipationRepository.save(participationInfo);
        }

        String title = battleInfo.getTitle();
        String content = battleInfo.getContent();

        // 배틀 케이스 정보 불러오기 (인풋, 순서)
        List<BattleCase> battleCaseList = battleCaseRepository.findByDailyBattle_BattleIdOrderByDisplayOrderAsc(battleId);

        // 배틀 케이스 응답 dto 만들기
        List<BattleCaseResponseDto>  battleCaseResponseList = new ArrayList<>();

        for (BattleCase battleCase : battleCaseList){
            String input = battleCase.getInput();
            Integer displayOrder = battleCase.getDisplayOrder();

            BattleCaseResponseDto battleCases = new BattleCaseResponseDto(input, displayOrder);
            battleCaseResponseList.add(battleCases);
        }

        // 남은 시간 계산 (마감이 KST 기준 시각이라 현재 시각도 KST로 계산)
        LocalDateTime now = LocalDateTime.now(clock);

        LocalDateTime battleEndTime = battleInfo.getBattleDate()
                .atTime(12, 10);

        Long remainedTimeSecond = Math.max(
                0L,
                Duration.between(now, battleEndTime).getSeconds()
        );

        // 새로운 배틀 참여 응답 dto 만들기
        BattleParticipationResponseDto battleParticipationResponseDto = new BattleParticipationResponseDto(
                battleInfo.getCategory(),
                battleId,
                userId,
                battleInfo.getBattleDate(),
                title,
                content,
                participationInfo.getParticipationStatus(),
                remainedTimeSecond,
                battleCaseResponseList
        );

        //반환
        return battleParticipationResponseDto;
    }

    // 중도 포기 메소드
    @Transactional
    public BattleDropOutResponseDto dropOutBattle(Long battleId, Long userId){

        // 배틀 정보 조회
        DailyBattle dailyBattleInfo = dailyBattleRepository.findById(battleId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_Not_Found")
        );

        // 중도 포기 가능 날짜, 시간 검증
        Instant receivedAt = clock.instant();

        battleDateCheck(receivedAt, dailyBattleInfo.getBattleDate());
        battleTimeCheck(receivedAt);

        // 참여 정보 조회
        BattleParticipation battleParticipation = battleParticipationRepository.findByDailyBattleIdAndUser_Id(battleId, userId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Participation_Not_Found")
        );

        // 중도 포기 처리
        if (battleParticipation.getParticipationStatus() != ParticipationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Battle_Cannot_Drop_Out"
            );
        }

        battleParticipation.markDropOut();

        // 응답 조립
        BattleDropOutResponseDto battleDropOutResponse = new BattleDropOutResponseDto(
                dailyBattleInfo.getBattleId(),
                battleParticipation.getParticipationId(),
                ParticipationStatus.DROPPED_OUT
        );

        return battleDropOutResponse;
    }

    // 배틀 제출 메소드
    @Transactional
    public BattleSubmissionResponseDto battleSubmission(
            DailyBattleSubmissionRequestDto battleSubmissionRequest,
            Long battleId,
            Long userId,
            Instant submittedAt){

        // 0. 배틀 존재 확인
        DailyBattle battleInfo = battleValidCheck(battleId);

        // 1.  배틀 날짜, 시간 확인
        battleDateCheck(submittedAt, battleInfo.getBattleDate());
        battleTimeCheck(submittedAt);

        // 2. 배틀 참여 정보 확인
        BattleParticipation participationInfo = battleParticipationCheck(battleId, userId);

        // 3. 참여 정보가 없으면 404
        if (participationInfo == null){
            throw new BusinessException(HttpStatus.NOT_FOUND, "Battle_participation_information_not_found");
        }

        // 요청 접수 시간 반환용
        LocalDateTime submittedTime = submittedAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime();

        // 4. 제출 상태로 변경
        Integer updatedCountCheck = battleParticipationRepository.updateStatusIfMatch(
                participationInfo.getParticipationId(),
                ParticipationStatus.IN_PROGRESS,
                ParticipationStatus.SUBMITTED,
                submittedTime
        );

        // 5. 상태 변경 실패 시 중복 제출 예외
        // 현재 상태가 IN_PROGRESS가 아니면 UPDATE가 실행되지 않고 0 반환
        if (updatedCountCheck == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Battle_already_participated"
            );
        }

        // 6. 제출 답안 3개 저장
        List<DailyBattleSubmissionRequestDto.Answer> userAnswers = battleSubmissionRequest.userAnswers();

        List<BattleCase> battleCaseList = battleCaseRepository.findByDailyBattle_BattleIdOrderByDisplayOrderAsc(battleId);

        // 행 저장용 리스트
        List<BattleSubmission> submissions = new ArrayList<>();

        // 요청 정답의 케이스 순서 중복 검증
        Set<Integer> caseNums = new HashSet<>();

        for (DailyBattleSubmissionRequestDto.Answer answer : userAnswers) {
            if (!caseNums.add(answer.caseNum())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Battle_case_number_duplicated"
                );
            }
        }

        for (DailyBattleSubmissionRequestDto.Answer answer : userAnswers) {

            // 6-1. caseNum에 해당하는 BattleCase 찾기
            BattleCase battleCase = null;

            for (BattleCase bc : battleCaseList) {
                if (bc.getDisplayOrder().equals(answer.caseNum())) {
                    battleCase = bc;
                    break;
                }
            }

            // 일치하는 BattleCase가 없으면 예외
            if (battleCase == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Battle_case_not_found"
                );
            }

            // 6-2. BattleSubmission 엔티티 생성
            BattleSubmission submission = new BattleSubmission(
                    participationInfo,
                    battleCase,
                    answer.userAnswer()
            );

            // 6-3. 저장 리스트에 추가
            submissions.add(submission);
        }

        // 6-4. 답안 3개 저장
        battleSubmissionRepository.saveAll(submissions);

        // 7. 응답 dto 만들기
        BattleSubmissionResponseDto battleSubmissionResponse = new BattleSubmissionResponseDto(
                battleId,
                userId,
                ParticipationStatus.SUBMITTED,
                submittedTime
        );

        return battleSubmissionResponse;
    }

    // 기존 참여 확인 메소드 → 해당 배틀에 대한 유저의 제출 여부 확인
    @Transactional(readOnly = true)
    private BattleParticipation battleParticipationCheck(Long battleId, Long userId){

        Optional<BattleParticipation> battleParticipationCheck = battleParticipationRepository.findByDailyBattleIdAndUser_Id(battleId, userId);

        // 참여 행이 있으면 참여정보 전달, 없으면 Null
        if(battleParticipationCheck.isPresent()){
            return battleParticipationCheck.get();
        } else {
            return null;
        }

    }

    // 배틀 존재 확인
    @Transactional(readOnly = true)
    private DailyBattle battleValidCheck(Long battleId){

        DailyBattle dailyBattle = dailyBattleRepository.findById(battleId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_not_found")
        );

        return dailyBattle;
    }


    // 배틀 날짜 확인 (제출한 배틀이 오늘 것이 맞는지
    private void battleDateCheck(Instant receivedAt, LocalDate battleDate) {

        // 요청 접수 시각을 한국 날짜로 변환하고
        LocalDate receivedDate = receivedAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();

        // 배틀 날짜와 비교
        if (!receivedDate.equals(battleDate)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "Not_Battle_Date"
            );
        }
    }


    // 배틀 제출 시간 유효 확인
    private void battleTimeCheck(Instant receivedAt) {

        // 요청 접수 시각을 한국 시간으로 변환
        LocalTime receivedTime = receivedAt.atZone(ZoneId.of("Asia/Seoul")).toLocalTime();

        // 배틀 시작, 마감 시각
        LocalTime start = LocalTime.of(12, 0);
        LocalTime end = LocalTime.of(12, 9, 59);

        // 시간 유효한지 검증 (12:00:00.000 이상 ~ 12:09:59.000 미만에 접수된 요청만 유효)
        if (receivedTime.isBefore(start) || !receivedTime.isBefore(end)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "Not_Battle_Time"
            );
        }
    }

    // 배틀 타임 아웃 처리
    @Transactional
    public void timeoutInProgress(Long battleId) {
        // 1. 해당 배틀에서 아직 참여 중인 사용자만 조회
        List<BattleParticipation> inProgressParticipation = battleParticipationRepository.findAllByDailyBattleIdAndParticipationStatus(
                battleId,
                ParticipationStatus.IN_PROGRESS);

        // 2. 타임 아웃 상태로 변경
        for (BattleParticipation participation : inProgressParticipation) {
            participation.markTimeOut();
        }
    }

}
