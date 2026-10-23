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
import com.cosmos.cosmos_backend.dailyBattle.dto.response.*;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.global.config.BattleSchedule;
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

    private final BattleSchedule battleSchedule;

    @Transactional
    public Long createDailyBattle(AiBattleCreateRequestDto request){

        LocalDate date = LocalDate.now(clock).plusDays(1);

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

    // 오늘 배틀 정보 조회
    @Transactional(readOnly = true)
    public CurrentDailyBattleResponseDto getCurrentDailyBattle() {

        LocalDateTime now = LocalDateTime.now(clock);

        LocalDate todayDate = now.toLocalDate();

        Long yesterdayBattleId = null;
        Long todayBattleId = null;

        Optional<DailyBattle> yesterdayBattle =
                dailyBattleRepository.findByBattleDate(todayDate.minusDays(1));

        if (yesterdayBattle.isPresent()) {
            yesterdayBattleId = yesterdayBattle.get().getBattleId();
        }

        Optional<DailyBattle> todayBattle =
                dailyBattleRepository.findByBattleDate(todayDate);

        if (todayBattle.isPresent()) {
            todayBattleId = todayBattle.get().getBattleId();
        }

        CurrentDailyBattleResponseDto currentDailyBattleResponse =
                new CurrentDailyBattleResponseDto(
                        now,
                        todayBattleId,
                        yesterdayBattleId
                );

        return currentDailyBattleResponse;
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
                .atTime(battleSchedule.resultTime());

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
        Instant deadline = battleInfo.getBattleDate()
                .atTime(battleSchedule.submissionDeadline())
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();

        // 타임아웃이 먼저 커밋했더라도 요청 접수 시각이 마감 전이면 제출 인정
        int updatedCountCheck = battleParticipationRepository.submitIfReceivedBeforeDeadline(
                participationInfo.getParticipationId(),
                ParticipationStatus.IN_PROGRESS,
                ParticipationStatus.TIME_OVER,
                ParticipationStatus.SUBMITTED,
                submittedAt,
                deadline
                );

        // 5. 상태 변경 실패 시 중복 제출 예외
        // 현재 상태가 IN_PROGRESS 또는 TIME_OVER가 아니거나, 요청 접수 시각이 마감 이후이면 UPDATE 결과 0 반환
        if (updatedCountCheck == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Battle_already_participated"
            );
        }

        // 조건부 업데이트 clearAutomatically 실행 이후 답안 연관관계에 사용할 관리 상태 참조를 다시 가져옴
        BattleParticipation participationReference = battleParticipationRepository.getReferenceById(
                participationInfo.getParticipationId()
        );

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
                    participationReference,
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

        // 설정된 시작 시각 이상, 제출 마감 시각 미만에 접수된 요청만 유효
        if (receivedTime.isBefore(battleSchedule.openTime())
                || !receivedTime.isBefore(battleSchedule.submissionDeadline())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "Not_Battle_Time"
            );
        }
    }

    // 배틀 타임 아웃 처리
    @Transactional
    public void timeoutInProgress(Long battleId) {
        // 참여중 상태만 직접 조건부 UPDATE (더티 체킹 방지)
        battleParticipationRepository.timeoutIfInProgress(
                battleId,
                ParticipationStatus.IN_PROGRESS,
                ParticipationStatus.TIME_OVER
        );
    }

}
