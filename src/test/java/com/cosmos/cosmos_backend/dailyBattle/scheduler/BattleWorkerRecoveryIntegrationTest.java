package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleGradingService;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleResultSaveService;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleResultService;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
import com.cosmos.cosmos_backend.dailyBattle.worker.BattleGradingWorker;
import com.cosmos.cosmos_backend.global.config.JpaConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl=OFF",
        "logging.level.com.cosmos.cosmos_backend.dailyBattle.scheduler.BattleWorkerRecoveryIntegrationTest=INFO"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        BattleGradingWorker.class,
        BattleGradingService.class,
        BattleResultSaveService.class,
        BattleResultService.class,
        DailyBattleService.class,
        JpaConfig.class,
        BattleWorkerRecoveryIntegrationTest.TestSupportConfig.class
})
class BattleWorkerRecoveryIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BattleWorkerRecoveryIntegrationTest.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalTime OPEN_TIME = LocalTime.of(10, 0);
    private static final LocalTime SUBMISSION_DEADLINE = LocalTime.of(10, 10);
    private static final LocalTime RESULT_TIME = LocalTime.of(10, 11);
    private static final Duration WORKER_DELAY = Duration.ofMillis(100);

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    private BattleWorkerScheduler battleWorkerScheduler;

    @Autowired
    private BattleResultService battleResultService;

    @Autowired
    private DailyBattleService dailyBattleService;

    @Autowired
    private BattleSchedule battleSchedule;

    @Autowired
    private BattleGradingWorker battleGradingWorker;

    @Autowired
    private BattleGradingService battleGradingService;

    @Autowired
    private DailyBattleRepository dailyBattleRepository;

    @Autowired
    private BattleParticipationRepository battleParticipationRepository;

    @Autowired
    private BattleSubmissionRepository battleSubmissionRepository;

    @Autowired
    private BattleCaseRepository battleCaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private TaskScheduler taskScheduler;

    @MockitoBean
    private BattleTimeoutScheduler battleTimeoutScheduler;

    private ScheduledFuture<?> firstWorkerTask;
    private ScheduledFuture<?> secondWorkerTask;
    private ScheduledFuture<?> recheckTask;

    @BeforeEach
    void setUp() {
        //given: 각 테스트가 독립적으로 실행되도록 기존 데이터를 제거
        transactionTemplate.executeWithoutResult(status -> {
            battleSubmissionRepository.deleteAllInBatch();
            battleParticipationRepository.deleteAllInBatch();
            battleCaseRepository.deleteAllInBatch();
            dailyBattleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });

        //given: 워커 작업 2개와 결과 재확인 작업을 구분해서 검증할 가짜 예약 작업 준비
        firstWorkerTask = mock(ScheduledFuture.class);
        secondWorkerTask = mock(ScheduledFuture.class);
        recheckTask = mock(ScheduledFuture.class);

        doReturn(firstWorkerTask, secondWorkerTask, recheckTask)
                .when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), eq(WORKER_DELAY));

        //given: 각 테스트가 이전 테스트의 실행 중인 워커 상태를 공유하지 않도록 스케줄러를 새로 생성
        battleWorkerScheduler = new BattleWorkerScheduler(
                battleResultService,
                dailyBattleService,
                battleParticipationRepository,
                dailyBattleRepository,
                clock,
                taskScheduler,
                battleGradingWorker,
                battleSchedule,
                battleTimeoutScheduler
        );
    }

    // 오늘 배틀이 없으면 서버 재시작 복구 작업을 실행하지 않는지 확인하는 테스트
    @Test
    void recoveryDoesNothingWhenTodayBattleDoesNotExist() {
        //given: 서버가 배틀 진행 시간에 재시작됐지만 오늘 배틀은 없고 어제 배틀만 존재
        clock.set(TODAY.atTime(OPEN_TIME.plusMinutes(1)));
        saveBattle(TODAY.minusDays(1), false);

        //when: 서버 재시작 복구 로직 실행
        battleWorkerScheduler.recoverAfterRestart();

        //then: 복구 대상이 아니므로 워커와 결과 재확인 작업을 시작하지 않음
        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), eq(WORKER_DELAY));

        printTestResult("오늘 배틀이 없는 서버 재시작",
                "복구 작업 실행 여부 : 실행 안 함",
                "워커 생성 수       : 0");
    }

    // 오늘 배틀 결과가 이미 공개됐다면 워커와 결과 확정 작업을 다시 시작하지 않는지 확인하는 테스트
    @Test
    void recoveryDoesNothingWhenTodayResultIsAlreadyPublished() {
        //given: 오늘 배틀 결과가 이미 공개된 뒤 서버가 다시 시작
        clock.set(TODAY.atTime(RESULT_TIME.plusMinutes(1)));
        saveBattle(TODAY, true);

        //when: 서버 재시작 복구 로직 실행
        battleWorkerScheduler.recoverAfterRestart();

        //then: 이미 완료된 배틀이므로 워커와 결과 재확인 작업을 중복 시작하지 않음
        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), eq(WORKER_DELAY));

        printTestResult("결과 공개가 끝난 서버 재시작",
                "중복 결과 공개 : 없음",
                "워커 생성 수   : 0");
    }

    // 배틀 시작 전에 서버가 재시작되면 워커를 즉시 시작하지 않는지 확인하는 테스트
    @Test
    void recoveryDoesNothingBeforeBattleOpenTime() {
        //given: 오늘 배틀은 있지만 배틀 시작 직전에 서버가 재시작
        clock.set(TODAY.atTime(OPEN_TIME.minusNanos(1)));
        saveBattle(TODAY, false);

        //when: 서버 재시작 복구 로직 실행
        battleWorkerScheduler.recoverAfterRestart();

        //then: 시작 시각 전이므로 예약된 정상 시작 작업에 맡기고 즉시 워커를 실행하지 않음
        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), eq(WORKER_DELAY));

        printTestResult("배틀 시작 전 서버 재시작",
                "현재 시각      : " + OPEN_TIME.minusNanos(1),
                "워커 생성 수   : 0");
    }

    // 배틀 진행 중 서버가 재시작되면 워커 2개가 만료된 GRADING 작업을 재선점해 채점하는지 확인하는 테스트
    @Test
    void recoveryStartsTwoWorkersWithTodayBattleIdDuringBattle() {
        //given: 기존 워커가 작업을 선점한 직후 서버가 종료되어 만료된 GRADING 작업이 남음
        clock.set(TODAY.atTime(OPEN_TIME.plusMinutes(1)));
        Long battleId = saveBattle(TODAY, false);
        Long participationId = saveGradingParticipation(battleId);
        battleGradingService.preemptSubmission(battleId).orElseThrow();
        expireLease(participationId);

        //when: 서버 재시작 복구 로직 실행
        battleWorkerScheduler.recoverAfterRestart();

        //then: 오늘 배틀 ID를 가진 워커 작업 2개만 등록
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, times(2)).scheduleWithFixedDelay(taskCaptor.capture(), eq(WORKER_DELAY));

        //then: 등록된 작업이 실제 실행되면 만료된 작업을 재선점해 채점 완료
        taskCaptor.getAllValues().getFirst().run();
        assertThat(findParticipation(participationId).getParticipationStatus())
                .isEqualTo(ParticipationStatus.GRADING_COMPLETED);
        assertThat(findParticipation(participationId).getLeaseRetryCount()).isEqualTo(1);

        printTestResult("진행 중 서버 재시작 후 만료 작업 복구",
                "배틀 ID        : " + battleId,
                "최종 상태      : " + findParticipation(participationId).getParticipationStatus(),
                "임대 재선점 횟수: " + findParticipation(participationId).getLeaseRetryCount());
    }

    // 제출 마감 시각에 재시작되면 IN_PROGRESS만 TIME_OVER 처리하고 제출 작업은 유지하는지 확인하는 테스트
    @Test
    void recoveryTimesOutOnlyInProgressParticipantsAtSubmissionDeadline() {
        //given: 제출 마감 시각에 서버가 재시작하고 미제출자와 제출자가 함께 존재
        clock.set(TODAY.atTime(SUBMISSION_DEADLINE));
        Long battleId = saveBattle(TODAY, false);
        Long inProgressId = saveParticipation(battleId, ParticipationStatus.IN_PROGRESS, "recovery-in-progress-user");
        Long submittedId = saveParticipation(battleId, ParticipationStatus.SUBMITTED, "recovery-submitted-user");

        //when: 서버 재시작 복구 로직 실행
        battleWorkerScheduler.recoverAfterRestart();

        //then: 미제출 참여자만 TIME_OVER 처리하고 제출된 채점 작업은 유지
        assertThat(findParticipation(inProgressId).getParticipationStatus())
                .isEqualTo(ParticipationStatus.TIME_OVER);
        assertThat(findParticipation(submittedId).getParticipationStatus())
                .isEqualTo(ParticipationStatus.SUBMITTED);

        //then: 남아 있는 제출 작업을 처리할 워커 2개 시작
        verify(taskScheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), eq(WORKER_DELAY));

        printTestResult("제출 마감 시각 서버 재시작",
                "미제출 참여 상태 : " + findParticipation(inProgressId).getParticipationStatus(),
                "제출 참여 상태   : " + findParticipation(submittedId).getParticipationStatus(),
                "워커 생성 수     : 2");
    }

    // 결과 공개 시각 이후 남은 채점을 완료한 뒤 재확인 작업이 결과를 공개하는지 확인하는 테스트
    @Test
    void recoveryRechecksPendingTaskAndPublishesResultAfterItFinishes() {
        //given: 결과 공개 시각이 지났지만 서버가 내려간 사이 제출 작업 하나가 미처리 상태로 남음
        clock.set(TODAY.atTime(RESULT_TIME.plusSeconds(1)));
        Long battleId = saveBattle(TODAY, false);
        Long participationId = saveGradingParticipation(battleId);

        //when: 서버 재시작 복구 로직 실행
        battleWorkerScheduler.recoverAfterRestart();

        //then: 결과를 조기 공개하지 않고 워커 2개와 결과 재확인 작업 1개를 등록
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, times(3)).scheduleWithFixedDelay(taskCaptor.capture(), eq(WORKER_DELAY));
        assertThat(findBattle(battleId).getResultPublishedAt()).isNull();

        //when: 복구된 워커가 실제 제출 답안을 선점해 채점
        taskCaptor.getAllValues().getFirst().run();

        //then: 결과 재확인 전에 실제 채점 작업이 완료됨
        assertThat(findParticipation(participationId).getParticipationStatus())
                .isEqualTo(ParticipationStatus.GRADING_COMPLETED);

        //when: 등록된 결과 재확인 작업이 다시 실행
        taskCaptor.getAllValues().get(2).run();

        //then: 미완료 작업이 사라진 뒤에만 결과를 공개하고 모든 반복 작업 종료
        assertThat(findBattle(battleId).getResultPublishedAt())
                .isEqualTo(TODAY.atTime(RESULT_TIME.plusSeconds(1)));
        verify(firstWorkerTask).cancel(false);
        verify(secondWorkerTask).cancel(false);
        verify(recheckTask).cancel(false);

        printTestResult("결과 시각 이후 미완료 작업 복구",
                "최종 작업 상태 : " + findParticipation(participationId).getParticipationStatus(),
                "결과 공개 시각 : " + findBattle(battleId).getResultPublishedAt(),
                "재확인 작업 종료: true");
    }

    // 결과 공개 시각 이후 남은 채점 작업이 없으면 즉시 결과를 공개하고 워커를 종료하는지 확인하는 테스트
    @Test
    void recoveryPublishesResultImmediatelyWhenNoGradingTaskRemains() {
        //given: 결과 공개 시각에 서버가 재시작했고 미제출 참여자만 남아 있음
        clock.set(TODAY.atTime(RESULT_TIME));
        Long battleId = saveBattle(TODAY, false);
        Long participationId = saveParticipation(
                battleId,
                ParticipationStatus.IN_PROGRESS,
                "recovery-timeout-user"
        );

        //when: 서버 재시작 복구 로직 실행
        battleWorkerScheduler.recoverAfterRestart();

        //then: 미제출자를 TIME_OVER 처리하고 결과를 현재 시각으로 공개
        assertThat(findParticipation(participationId).getParticipationStatus())
                .isEqualTo(ParticipationStatus.TIME_OVER);
        assertThat(findBattle(battleId).getResultPublishedAt())
                .isEqualTo(TODAY.atTime(RESULT_TIME));

        //then: 시작했던 워커 2개를 종료하고 불필요한 결과 재확인 작업은 만들지 않음
        verify(taskScheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), eq(WORKER_DELAY));
        verify(firstWorkerTask).cancel(false);
        verify(secondWorkerTask).cancel(false);
        verify(recheckTask, never()).cancel(false);

        printTestResult("결과 시각 이후 즉시 결과 확정",
                "미제출 참여 상태 : " + findParticipation(participationId).getParticipationStatus(),
                "결과 공개 시각   : " + findBattle(battleId).getResultPublishedAt(),
                "재확인 작업 생성 : 없음");
    }

    // 서버 재시작 복구 로직이 중복 호출돼도 워커가 2개를 초과해 생성되지 않는지 확인하는 테스트
    @Test
    void repeatedRecoveryDoesNotCreateDuplicateWorkers() {
        //given: 배틀 진행 중 같은 서버에서 복구 이벤트가 중복 호출될 수 있는 상황
        clock.set(TODAY.atTime(OPEN_TIME.plusMinutes(1)));
        saveBattle(TODAY, false);

        //when: 서버 재시작 복구 로직을 연속 두 번 실행
        battleWorkerScheduler.recoverAfterRestart();
        battleWorkerScheduler.recoverAfterRestart();

        //then: 실행 중인 워커를 확인해 총 2개를 초과해서 생성하지 않음
        verify(taskScheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), eq(WORKER_DELAY));

        printTestResult("복구 이벤트 중복 실행",
                "복구 호출 횟수 : 2",
                "최종 워커 수   : 2");
    }

    // 실제 스케줄러 스레드가 워커를 반복 실행하고 취소 후에는 추가 실행하지 않는지 확인하는 테스트
    @Test
    void realTaskSchedulerRepeatsWorkersAndStopsAfterCancellation() throws InterruptedException {
        //given: 실제 스케줄러 스레드에서 워커 반복 실행 횟수를 기록할 카운터와 종료 신호 준비
        clock.set(TODAY.atTime(OPEN_TIME.plusMinutes(1)));
        saveBattle(TODAY, false);
        AtomicInteger executionCount = new AtomicInteger();
        CountDownLatch repeatedExecution = new CountDownLatch(4);
        BattleGradingWorker countingWorker = mock(BattleGradingWorker.class);

        doAnswer(invocation -> {
            executionCount.incrementAndGet();
            repeatedExecution.countDown();
            return null;
        }).when(countingWorker).gradingBattleSubmissions(any(Long.class));

        ThreadPoolTaskScheduler realTaskScheduler = new ThreadPoolTaskScheduler();
        realTaskScheduler.setPoolSize(2);
        realTaskScheduler.setThreadNamePrefix("battle-recovery-test-");
        realTaskScheduler.setRemoveOnCancelPolicy(true);
        realTaskScheduler.initialize();

        BattleWorkerScheduler realScheduler = new BattleWorkerScheduler(
                battleResultService,
                dailyBattleService,
                battleParticipationRepository,
                dailyBattleRepository,
                clock,
                realTaskScheduler,
                countingWorker,
                battleSchedule,
                battleTimeoutScheduler
        );

        try {
            //when: 실제 스케줄러에 워커 2개를 등록
            realScheduler.startGradingWorker();

            //then: 실제 스레드에서 두 워커가 고정 지연으로 반복 실행
            assertThat(repeatedExecution.await(2, TimeUnit.SECONDS)).isTrue();

            //when: 워커 반복 작업 취소
            realScheduler.stopGradingWorker();
            Thread.sleep(WORKER_DELAY.multipliedBy(2).toMillis());
            int countAfterCancellation = executionCount.get();
            Thread.sleep(WORKER_DELAY.multipliedBy(3).toMillis());

            //then: 취소가 완료된 뒤에는 실행 횟수가 더 증가하지 않음
            assertThat(executionCount.get()).isEqualTo(countAfterCancellation);

            printTestResult("실제 스케줄러 반복 실행 및 취소",
                    "취소 전후 실행 횟수 : " + countAfterCancellation,
                    "취소 후 추가 실행   : 없음");
        } finally {
            //then: 테스트 실패 여부와 관계없이 실제 스케줄러 스레드 종료
            realScheduler.stopGradingWorker();
            realTaskScheduler.destroy();
        }
    }

    private Long saveBattle(LocalDate battleDate, boolean published) {
        return transactionTemplate.execute(status -> {
            DailyBattle battle = new DailyBattle(
                    battleDate,
                    Category.ARRAY,
                    "worker recovery integration battle",
                    "server restart recovery integration test"
            );

            if (published) {
                battle.publishResult(battleDate.atTime(RESULT_TIME));
            }

            return dailyBattleRepository.saveAndFlush(battle).getBattleId();
        });
    }

    private Long saveParticipation(Long battleId, ParticipationStatus status, String userName) {
        return transactionTemplate.execute(transactionStatus -> {
            User user = userRepository.save(new User(userName, null));
            BattleParticipation participation = new BattleParticipation(battleId, user);

            if (status == ParticipationStatus.SUBMITTED) {
                participation.markSubmit(clock.instant());
            }

            return battleParticipationRepository.saveAndFlush(participation).getParticipationId();
        });
    }

    private Long saveGradingParticipation(Long battleId) {
        return transactionTemplate.execute(status -> {
            DailyBattle battle = dailyBattleRepository.findById(battleId).orElseThrow();
            User user = userRepository.save(new User("recovery-grading-user", null));
            BattleCase battleCase = battleCaseRepository.save(new BattleCase(
                    battle,
                    "1 2",
                    "3",
                    1
            ));

            BattleParticipation participation = new BattleParticipation(battleId, user);
            participation.markSubmit(clock.instant());
            participation = battleParticipationRepository.save(participation);

            battleSubmissionRepository.save(new BattleSubmission(
                    participation,
                    battleCase,
                    "3"
            ));

            return participation.getParticipationId();
        });
    }

    private void expireLease(Long participationId) {
        jdbcTemplate.update("""
                UPDATE battle_participations
                   SET lease_expires_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 10 SECOND)
                 WHERE participation_id = ?
                """, participationId);
    }

    private DailyBattle findBattle(Long battleId) {
        return dailyBattleRepository.findById(battleId).orElseThrow();
    }

    private BattleParticipation findParticipation(Long participationId) {
        return battleParticipationRepository.findById(participationId).orElseThrow();
    }

    private void printTestResult(String scenario, String... details) {
        log.info("""

                ==================== 서버 재시작 복구 통합 테스트 결과 ====================
                시나리오 : {}
                {}
                ========================================================================
                """, scenario, String.join("\n", details));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfig {

        @Bean
        MutableClock clock() {
            return new MutableClock(KOREA_ZONE);
        }

        @Bean
        BattleSchedule battleSchedule() {
            return new BattleSchedule(OPEN_TIME, SUBMISSION_DEADLINE, RESULT_TIME);
        }

    }

    static class MutableClock extends Clock {

        private final ZoneId zone;
        private Instant instant;

        MutableClock(ZoneId zone) {
            this.zone = zone;
            this.instant = Instant.EPOCH;
        }

        void set(LocalDateTime dateTime) {
            this.instant = dateTime.atZone(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
