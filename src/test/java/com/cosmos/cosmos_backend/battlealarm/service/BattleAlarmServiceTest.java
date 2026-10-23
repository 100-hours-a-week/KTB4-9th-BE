package com.cosmos.cosmos_backend.battlealarm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarm;
import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarmStatus;
import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarmTimePolicy;
import com.cosmos.cosmos_backend.battlealarm.dto.response.BattleAlarmStatusResponse;
import com.cosmos.cosmos_backend.battlealarm.repository.BattleAlarmRepository;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.global.config.BattleSchedule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BattleAlarmServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    @Mock
    private BattleAlarmRepository battleAlarmRepository;

    @Mock
    private DailyBattleRepository dailyBattleRepository;

    @Mock
    private BattleCaseRepository battleCaseRepository;

    @Mock
    private BattleParticipationRepository battleParticipationRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    // 한국 시각 기준으로 고정한 시계로 서비스를 만든다.
    private BattleAlarmService serviceAt(String kstTime) {
        Clock clock = Clock.fixed(OffsetDateTime.parse(kstTime).toInstant(), ZoneId.of("Asia/Seoul"));
        BattleAlarmTimePolicy timePolicy = new BattleAlarmTimePolicy(
                new BattleSchedule(
                        LocalTime.of(12, 50),
                        LocalTime.of(12, 59, 59),
                        LocalTime.of(13, 0)
                )
        );
        return new BattleAlarmService(
                battleAlarmRepository, dailyBattleRepository, battleCaseRepository,
                battleParticipationRepository, transactionTemplate, clock, timePolicy
        );
    }

    // 오늘 3번 배틀이 있고 케이스가 caseCount개 등록된 상태로 만든다.
    private void stubTodayBattleWithCases(int caseCount) {
        DailyBattle battle = mock(DailyBattle.class);
        when(battle.getBattleId()).thenReturn(3L);
        List<BattleCase> cases = IntStream.range(0, caseCount).mapToObj(i -> mock(BattleCase.class)).toList();

        when(dailyBattleRepository.findByBattleDate(TODAY)).thenReturn(Optional.of(battle));
        when(battleCaseRepository.findByDailyBattle_BattleIdOrderByDisplayOrderAsc(3L)).thenReturn(cases);
    }

    // 트랜잭션 안의 작업을 바로 실행하는 것처럼 흉내 낸다.
    private void runTransactionInline() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void getStatus_returnsNotOpen_andSkipsDb_before1250() {
        // When
        BattleAlarmStatusResponse response = serviceAt("2026-10-13T12:49:59+09:00").getStatus(7L);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.NOT_OPEN);
        verifyNoInteractions(dailyBattleRepository, battleCaseRepository,
                battleParticipationRepository, battleAlarmRepository);
    }

    @Test
    void getStatus_returnsOpen_whenBattleReady_andNotParticipatedNorDismissed() {
        // Given: 12:50:00 정각, 배틀과 케이스 3개가 준비됨
        stubTodayBattleWithCases(3);
        when(battleParticipationRepository.findByDailyBattleIdAndUser_Id(3L, 7L)).thenReturn(Optional.empty());
        when(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).thenReturn(false);

        // When
        BattleAlarmStatusResponse response = serviceAt("2026-10-13T12:50:00+09:00").getStatus(7L);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.OPEN);
        assertThat(response.battleId()).isEqualTo(3L);
        assertThat(response.participated()).isFalse();
        assertThat(response.dismissed()).isFalse();
        assertThat(response.serverTime()).isEqualTo(OffsetDateTime.parse("2026-10-13T12:50:00+09:00"));
        assertThat(response.closesAt()).isEqualTo(OffsetDateTime.parse("2026-10-13T13:00:00+09:00"));
    }

    @Test
    void getStatus_marksParticipated_whenUserAlreadyParticipated() {
        // Given: 사용자가 이미 참여함
        stubTodayBattleWithCases(3);
        BattleParticipation participation = mock(BattleParticipation.class);
        when(battleParticipationRepository.findByDailyBattleIdAndUser_Id(3L, 7L))
                .thenReturn(Optional.of(participation));
        when(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).thenReturn(false);

        // When
        BattleAlarmStatusResponse response = serviceAt("2026-10-13T12:55:00+09:00").getStatus(7L);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.OPEN);
        assertThat(response.participated()).isTrue();
    }

    @Test
    void getStatus_marksDismissed_whenUserAlreadyDismissed() {
        // Given: 사용자가 오늘 알림을 닫음
        stubTodayBattleWithCases(3);
        when(battleParticipationRepository.findByDailyBattleIdAndUser_Id(3L, 7L)).thenReturn(Optional.empty());
        when(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).thenReturn(true);

        // When
        BattleAlarmStatusResponse response = serviceAt("2026-10-13T12:55:00+09:00").getStatus(7L);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.OPEN);
        assertThat(response.dismissed()).isTrue();
    }

    @Test
    void getStatus_returnsNoBattle_whenNoBattleToday() {
        // Given: 열려 있어야 하는 시간인데 오늘 배틀이 없음
        when(dailyBattleRepository.findByBattleDate(TODAY)).thenReturn(Optional.empty());

        // When
        BattleAlarmStatusResponse response = serviceAt("2026-10-13T12:55:00+09:00").getStatus(7L);

        // Then: NO_BATTLE이고 케이스, 참여, 닫음은 조회하지 않음
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.NO_BATTLE);
        assertThat(response.battleId()).isNull();
        verifyNoInteractions(battleCaseRepository, battleParticipationRepository, battleAlarmRepository);
    }

    @Test
    void getStatus_returnsNoBattle_whenCasesAreNotThree() {
        // Given: 배틀은 있지만 케이스가 2개뿐
        stubTodayBattleWithCases(2);

        // When
        BattleAlarmStatusResponse response = serviceAt("2026-10-13T12:55:00+09:00").getStatus(7L);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.NO_BATTLE);
        verifyNoInteractions(battleParticipationRepository, battleAlarmRepository);
    }

    @Test
    void getStatus_returnsClosed_withBattleId_at1300() {
        // Given
        DailyBattle battle = mock(DailyBattle.class);
        when(battle.getBattleId()).thenReturn(3L);
        when(dailyBattleRepository.findByBattleDate(TODAY)).thenReturn(Optional.of(battle));

        // When
        BattleAlarmStatusResponse response = serviceAt("2026-10-13T13:00:00+09:00").getStatus(7L);

        // Then
        assertThat(response.status()).isEqualTo(BattleAlarmStatus.CLOSED);
        assertThat(response.battleId()).isEqualTo(3L);
        verifyNoInteractions(battleCaseRepository, battleParticipationRepository, battleAlarmRepository);
    }

    @Test
    void dismiss_throws404_whenBattleDoesNotExist() {
        // Given
        when(dailyBattleRepository.existsById(3L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> serviceAt("2026-10-13T12:55:00+09:00").dismiss(7L, 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getMessage()).isEqualTo("daily_battle_not_found");
                });
        verifyNoInteractions(battleAlarmRepository, transactionTemplate);
    }

    @Test
    void dismiss_doesNothing_whenAlreadyDismissed() {
        // Given
        when(dailyBattleRepository.existsById(3L)).thenReturn(true);
        when(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).thenReturn(true);

        // When
        serviceAt("2026-10-13T12:55:00+09:00").dismiss(7L, 3L);

        // Then: 저장하지 않음
        verifyNoInteractions(transactionTemplate);
        verify(battleAlarmRepository, never()).save(any());
    }

    @Test
    void dismiss_savesNewDismissedRow_whenNoRowExists() {
        // Given: 행이 없음
        when(dailyBattleRepository.existsById(3L)).thenReturn(true);
        when(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).thenReturn(false);
        when(battleAlarmRepository.findByUserIdAndBattleId(7L, 3L)).thenReturn(Optional.empty());
        runTransactionInline();

        // When
        serviceAt("2026-10-13T12:55:00+09:00").dismiss(7L, 3L);

        // Then: 닫음 상태의 새 행이 저장됨
        ArgumentCaptor<BattleAlarm> captor = ArgumentCaptor.forClass(BattleAlarm.class);
        verify(battleAlarmRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getBattleId()).isEqualTo(3L);
        assertThat(captor.getValue().isDismissed()).isTrue();
    }

    @Test
    void dismiss_updatesExistingRow_whenRowExistsButNotDismissed() {
        // Given: 행은 있지만 닫지 않은 상태
        BattleAlarm existing = new BattleAlarm(7L, 3L);
        when(dailyBattleRepository.existsById(3L)).thenReturn(true);
        when(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).thenReturn(false);
        when(battleAlarmRepository.findByUserIdAndBattleId(7L, 3L)).thenReturn(Optional.of(existing));
        runTransactionInline();

        // When
        serviceAt("2026-10-13T12:55:00+09:00").dismiss(7L, 3L);

        // Then: 그 행이 닫음으로 바뀌어 저장됨
        assertThat(existing.isDismissed()).isTrue();
        verify(battleAlarmRepository).save(existing);
    }

    @Test
    void dismiss_treatsUniqueConflictAsSuccess() {
        // Given: 저장하는 순간 다른 요청이 먼저 저장해서 유니크 제약 예외가 남
        when(dailyBattleRepository.existsById(3L)).thenReturn(true);
        when(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(transactionTemplate).executeWithoutResult(any());

        // When & Then: 예외 없이 끝남
        assertThatCode(() -> serviceAt("2026-10-13T12:55:00+09:00").dismiss(7L, 3L))
                .doesNotThrowAnyException();
    }
}
