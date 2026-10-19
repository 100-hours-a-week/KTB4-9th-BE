package com.cosmos.cosmos_backend.battlealarm.service;

import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarm;
import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarmTimePolicy;
import com.cosmos.cosmos_backend.battlealarm.dto.response.BattleAlarmStatusResponse;
import com.cosmos.cosmos_backend.battlealarm.repository.BattleAlarmRepository;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 배틀 알림 모달의 표시 상태를 계산하고, 사용자가 모달을 닫은 것을 저장함. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BattleAlarmService {

    // 배틀에 등록되어 있어야 하는 케이스 개수
    private static final int REQUIRED_CASE_COUNT = 3;

    private final BattleAlarmRepository battleAlarmRepository;
    private final DailyBattleRepository dailyBattleRepository;
    private final BattleCaseRepository battleCaseRepository;
    private final BattleParticipationRepository battleParticipationRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 서버 시각과 배틀 준비 여부로 모달 표시 상태를 계산. */
    public BattleAlarmStatusResponse getStatus(Long userId) {
        // 1. 서버 시각(KST)에서 오늘 날짜, 현재 시각, 마감 시각을 한 번에 구함
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        OffsetDateTime closesAt = BattleAlarmTimePolicy.closesAt(today, clock.getZone());

        // 2. 12:00 전이면 DB를 보지 않고 NOT_OPEN
        if (BattleAlarmTimePolicy.isBeforeOpen(time)) {
            return BattleAlarmStatusResponse.notOpen(now, closesAt);
        }

        // 3. 마감 이후에는 미참여자도 결과를 조회할 수 있도록 오늘의 배틀 ID를 함께 반환
        if (BattleAlarmTimePolicy.isClosed(time)) {
            Long battleId = dailyBattleRepository.findByBattleDate(today)
                    .map(DailyBattle::getBattleId)
                    .orElse(null);
            return BattleAlarmStatusResponse.closed(now, battleId, closesAt);
        }

        // 4. 열려 있어야 하는 시간이므로 오늘 배틀과 케이스가 준비됐는지 확인 (아니면 NO_BATTLE)
        Optional<DailyBattle> battle = dailyBattleRepository.findByBattleDate(today);
        if (battle.isEmpty() || !hasAllCases(battle.get().getBattleId())) {
            log.warn("배틀 알림 시간인데 오늘 배틀이 준비되지 않았습니다. date={}", today);
            return BattleAlarmStatusResponse.noBattle(now, closesAt);
        }

        // 5. 준비됐으면 이 사용자가 이미 참여했는지, 오늘 닫았는지 조회해서 OPEN으로 응답
        Long battleId = battle.get().getBattleId();
        boolean participated = battleParticipationRepository
                .findByDailyBattleIdAndUser_Id(battleId, userId).isPresent();
        boolean dismissed = battleAlarmRepository
                .existsByUserIdAndBattleIdAndDismissedTrue(userId, battleId);
        return BattleAlarmStatusResponse.open(now, battleId, participated, dismissed, closesAt);
    }

    /** 사용자가 배틀 알림 모달을 닫은 것을 저장. 이미 닫았거나 동시에 저장이 겹쳐도 성공으로 처리. */
    public void dismiss(Long userId, Long battleId) {
        // 1. 없는 배틀이면 404 예외를 던짐
        if (!dailyBattleRepository.existsById(battleId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "daily_battle_not_found");
        }

        // 2. 이미 닫았으면 저장하지 않고 끝냄
        if (battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(userId, battleId)) {
            return;
        }

        try {
            // 3. 닫음을 저장 (트랜잭션 안에서 저장하고 커밋까지 끝냄)
            transactionTemplate.executeWithoutResult(status -> saveDismissed(userId, battleId));
        } catch (DataIntegrityViolationException e) {
            // 4. 다른 요청이 같은 사용자·배틀을 먼저 저장해 유니크 제약이 걸린 것이므로 이미 닫은 상태라 성공으로 처리
        }
    }

    // 케이스가 정확히 3개 등록되어 있는지 확인
    private boolean hasAllCases(Long battleId) {
        return battleCaseRepository.findByDailyBattle_BattleIdOrderByDisplayOrderAsc(battleId).size()
                == REQUIRED_CASE_COUNT;
    }

    // 행이 있으면 그 행을, 없으면 새 행을 만들어 닫음 상태로 저장
    private void saveDismissed(Long userId, Long battleId) {
        BattleAlarm alarm = battleAlarmRepository.findByUserIdAndBattleId(userId, battleId)
                .orElseGet(() -> new BattleAlarm(userId, battleId));
        alarm.dismiss();
        battleAlarmRepository.save(alarm);
    }
}
