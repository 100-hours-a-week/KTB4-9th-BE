package com.cosmos.cosmos_backend.dailyBattle.service;

import static com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus.IN_PROGRESS;
import static com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus.SUBMITTED;
import static com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus.TIME_OVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.zaxxer.hikari.HikariDataSource;
import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.DailyBattleSubmissionRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleSubmissionResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.global.config.JpaConfig;
import com.cosmos.cosmos_backend.global.config.TimeConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl=OFF",
        "logging.level.com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleSubmissionTimeoutConcurrencyIntegrationTest=INFO"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        DailyBattleService.class,
        JpaConfig.class,
        TimeConfig.class,
        DailyBattleSubmissionTimeoutConcurrencyIntegrationTest.ConditionalUpdateGateConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
class DailyBattleSubmissionTimeoutConcurrencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(
            DailyBattleSubmissionTimeoutConcurrencyIntegrationTest.class);

    // 두 테스트가 같은 DB에서 실행돼도 데이터가 겹치지 않도록 기준 날짜만 공유
    private static final LocalDate BASE_BATTLE_DATE = LocalDate.of(2026, 10, 21);

    // 각 배틀 날짜의 참여 가능 시간인 한국 시각 12:55를 제출 시각으로 사용
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    // 동시성 테스트가 교착 상태로 무한 대기하지 않도록 최대 대기 시간 제한
    private static final int WAIT_SECONDS = 10;

    // 스트레스 테스트 참여자 수와 DB 작업 완료를 기다릴 최대 시간
    private static final int STRESS_USER_COUNT = 100;
    private static final int STRESS_WAIT_SECONDS = 60;
    private static final int TIMEOUT_START_DELAY_MILLIS = 1;

    @Autowired
    private DailyBattleService dailyBattleService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyBattleRepository dailyBattleRepository;

    @Autowired
    private BattleCaseRepository battleCaseRepository;

    @Autowired
    private BattleParticipationRepository battleParticipationRepository;

    @Autowired
    private BattleSubmissionRepository battleSubmissionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ConditionalUpdateGate conditionalUpdateGate;

    @Autowired
    private ConcurrencyTimelineRecorder timelineRecorder;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() throws InterruptedException {
        // 테스트 실패 시 조건부 UPDATE 앞에서 대기 중인 스레드가 남지 않도록 게이트 해제
        conditionalUpdateGate.release();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        timelineRecorder.reset();
    }

    @Test
    void timeoutReadsFirst_thenSubmissionCommits_thenTimeoutDirtyCheckingOverwritesIt() throws Exception {
        // 이전 시나리오: 제출 트랜잭션이 시작되기 전에 타임아웃 워커가 대상을 먼저 읽는다.
        Fixture fixture = createFixture(0);

        // 타임아웃 조회 완료와 제출 커밋 완료를 전달할 래치 준비
        CountDownLatch timeoutReadCompleted = new CountDownLatch(1);
        CountDownLatch submissionCommitted = new CountDownLatch(1);

        // 1. 타임아웃 트랜잭션을 먼저 시작해 IN_PROGRESS 참여자를 조회
        Future<?> timeout = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            List<BattleParticipation> loaded = battleParticipationRepository
                    .findAllByDailyBattleIdAndParticipationStatus(fixture.battleId(), IN_PROGRESS);
            assertThat(loaded).hasSize(1);

            // 제출 스레드가 시작할 수 있도록 타임아웃 조회 완료 신호 전달
            timeoutReadCompleted.countDown();

            // 제출의 상태 변경, 답안 저장, 트랜잭션 커밋이 모두 끝날 때까지 대기
            await(submissionCommitted, "submission transaction did not commit");

            // 먼저 조회한 stale 엔티티를 TIME_OVER로 변경 후 Dirty Checking 커밋
            loaded.forEach(BattleParticipation::markTimeOut);
        }));

        // 2. 타임아웃 조회가 끝난 뒤 실제 제출 서비스 트랜잭션 시작
        Future<BattleSubmissionResponseDto> submission = executor.submit(() -> {
            await(timeoutReadCompleted, "timeout transaction did not finish its IN_PROGRESS query");

            BattleSubmissionResponseDto response = dailyBattleService.battleSubmission(
                    submissionRequest(fixture.battleId()),
                    fixture.battleId(),
                    fixture.userId(),
                    fixture.submittedAt());

            // 서비스 호출 반환 시 @Transactional 커밋 완료, 타임아웃 스레드 재개
            submissionCommitted.countDown();
            return response;
        });

        // 3. 제출 커밋 완료 후 타임아웃 Dirty Checking 커밋까지 대기
        BattleSubmissionResponseDto response = get(submission);
        get(timeout);

        assertRaceWasReproduced("타임아웃 선조회 후 제출 시작", fixture, response);
    }

    @Test
    void timeoutReadsImmediatelyBeforeConditionalUpdate_thenDirtyCheckingOverwritesSubmission() throws Exception {
        // Given: IN_PROGRESS 참여자 1명과 제출할 배틀 문제 3개 생성
        Fixture fixture = createFixture(1);

        // 조건부 UPDATE 도달, 타임아웃 조회 완료, 제출 커밋 완료를 전달할 래치 준비
        CountDownLatch conditionalUpdateReached = new CountDownLatch(1);
        CountDownLatch timeoutReadCompleted = new CountDownLatch(1);
        CountDownLatch submissionCommitted = new CountDownLatch(1);

        // 제출 트랜잭션의 조건부 UPDATE 직전에 멈출 테스트 전용 게이트 활성화
        conditionalUpdateGate.arm(conditionalUpdateReached, timeoutReadCompleted);

        // 1. 실제 제출 서비스 트랜잭션을 먼저 시작
        Future<BattleSubmissionResponseDto> submission = executor.submit(() -> {
            // battleSubmission() 내부에서 참여자 조회까지 수행한 뒤
            // updateStatusIfMatch() 직전 테스트 게이트에서 일시 정지
            BattleSubmissionResponseDto response = dailyBattleService.battleSubmission(
                    submissionRequest(fixture.battleId()),
                    fixture.battleId(),
                    fixture.userId(),
                    fixture.submittedAt());

            // 서비스 호출 반환 시 @Transactional 커밋 완료, 타임아웃 스레드 재개
            submissionCommitted.countDown();
            return response;
        });

        // 2. 제출 조건부 UPDATE 직전에 타임아웃 트랜잭션 시작
        Future<?> timeout = executor.submit(() -> {
            await(conditionalUpdateReached, "submission did not reach its conditional update");

            transactionTemplate.executeWithoutResult(status -> {
                // DB 상태가 아직 IN_PROGRESS일 때 타임아웃 대상 조회
                List<BattleParticipation> loaded = battleParticipationRepository
                        .findAllByDailyBattleIdAndParticipationStatus(fixture.battleId(), IN_PROGRESS);
                assertThat(loaded).hasSize(1);

                // 타임아웃 조회 완료를 알려 제출 조건부 UPDATE 재개
                timeoutReadCompleted.countDown();

                // 제출의 상태 변경, 답안 저장, 트랜잭션 커밋이 모두 끝날 때까지 대기
                await(submissionCommitted, "submission transaction did not commit");

                // 먼저 조회한 stale 엔티티를 TIME_OVER로 변경 후 Dirty Checking 커밋
                loaded.forEach(BattleParticipation::markTimeOut);
            });
        });

        // 3. 제출 커밋 완료 후 타임아웃 Dirty Checking 커밋까지 대기
        BattleSubmissionResponseDto response = get(submission);
        get(timeout);

        assertRaceWasReproduced("조건부 UPDATE 직전 타임아웃 조회", fixture, response);
    }

    @Test
    void preDeadlineRequestStartsAfterTimeoutCommit_isRejectedByCurrentConditionalUpdate() throws Exception {
        // 반대 순서 시나리오: 요청은 마감 전에 도착했지만 제출 트랜잭션 시작이 늦어진다.
        Fixture fixture = createFixture(2);

        // 요청 도착과 타임아웃 커밋 완료를 작업 스레드 사이에 전달할 래치 준비
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch timeoutCommitted = new CountDownLatch(1);

        // 1. 제출 요청은 유효한 시각에 먼저 도착했지만 서비스 호출 직전에 대기
        Future<BattleSubmissionResponseDto> submission = executor.submit(() -> {
            requestReceived.countDown();
            await(timeoutCommitted, "timeout transaction did not commit");

            // 전달하는 submittedAt은 마감 전이지만 실제 제출 트랜잭션은 지금 시작된다.
            return dailyBattleService.battleSubmission(
                    submissionRequest(fixture.battleId()),
                    fixture.battleId(),
                    fixture.userId(),
                    fixture.submittedAt());
        });

        // 2. 요청 도착 후 제출 트랜잭션보다 타임아웃 트랜잭션을 먼저 커밋
        Future<?> timeout = executor.submit(() -> {
            await(requestReceived, "submission request did not arrive");

            transactionTemplate.executeWithoutResult(status -> {
                List<BattleParticipation> loaded = battleParticipationRepository
                        .findAllByDailyBattleIdAndParticipationStatus(fixture.battleId(), IN_PROGRESS);
                assertThat(loaded).hasSize(1);

                // TIME_OVER 변경은 transactionTemplate 반환 시점에 Dirty Checking으로 커밋된다.
                loaded.forEach(BattleParticipation::markTimeOut);
            });

            // 커밋이 끝난 뒤에만 제출 서비스가 실행되도록 신호 전달
            timeoutCommitted.countDown();
        });

        // 3. 타임아웃 커밋을 확인한 뒤 지연된 제출의 실패 결과 확인
        get(timeout);
        Throwable submissionFailure = catchThrowable(() -> get(submission));

        BattleParticipation persisted = battleParticipationRepository
                .findById(fixture.participationId())
                .orElseThrow();
        int savedSubmissionCount = battleSubmissionRepository
                .findByParticipation_participationId(fixture.participationId())
                .size();

        // 현재 제출 조건은 IN_PROGRESS만 허용하므로 이미 TIME_OVER이면 충돌 예외 발생
        assertThat(submissionFailure)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Battle_already_participated");
        assertThat(savedSubmissionCount).isZero();
        assertThat(persisted.getParticipationStatus()).isEqualTo(TIME_OVER);
        assertThat(persisted.getSubmittedAt()).isNull();

        // 마감 전 접수였어도 실행이 늦으면 제출이 유실되는 현재 동작을 콘솔에 출력
        log.info("""

                ==================== 동시성 테스트 결과 ====================
                시나리오              : 타임아웃 선커밋 후 마감 전 접수 요청 실행
                1. 제출 API 실행 결과 : {} ({})
                2. 제출 답안 저장     : {}건
                3. DB 최종 참여 상태  : {}
                4. DB 최종 제출 시각  : {}
                ===========================================================
                """,
                submissionFailure.getClass().getSimpleName(),
                submissionFailure.getMessage(),
                savedSubmissionCount,
                persisted.getParticipationStatus(),
                persisted.getSubmittedAt());
    }

    @Test
    void hundredPreDeadlineSubmissionsRaceWithTimeout_reportsObservedFailures() throws Exception {
        // Given: IN_PROGRESS 참여자 100명과 제출할 배틀 문제 3개 생성
        // 제출 접수 시각은 마감 시각(12:59:59)보다 1ms 빠른 12:59:58.999로 고정
        StressFixture fixture = createStressFixture(3, STRESS_USER_COUNT);

        // 시나리오 1, 2, 3을 구분할 수 있도록 이 배틀의 주요 실행 시각 기록 시작
        timelineRecorder.arm(fixture.battleId());

        // 테스트 분리해 순서 끼워넣기
        // workersReady: 제출 100개와 타임아웃 1개가 모두 준비됐는지 확인
        // startRace: 제출 요청을 출발시키고 타임아웃의 1ms 지연 측정을 시작하는 신호
        CountDownLatch workersReady = new CountDownLatch(STRESS_USER_COUNT + 1);
        CountDownLatch startRace = new CountDownLatch(1);

        List<Future<SubmissionAttempt>> submissionTasks = new ArrayList<>(STRESS_USER_COUNT);
        List<SubmissionAttempt> attempts = new ArrayList<>(STRESS_USER_COUNT);
        Throwable timeoutFailure = null;

        // 1. 타임아웃이 제출 작업 100개 뒤에 밀리지 않도록 실행기를 서로 분리
        // 제출은 100개가 동시에 대기할 수 있는 가상 스레드, 타임아웃은 전용 단일 스레드 사용
        ExecutorService submissionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService timeoutExecutor = Executors.newSingleThreadExecutor();
        try {
            // 2. 타임아웃 작업을 제출 작업보다 먼저 전용 실행기에 등록하고 출발선에서 대기
            Future<Throwable> timeoutTask = timeoutExecutor.submit(() -> {
                workersReady.countDown();
                await(startRace, "stress test did not start");

                // 제출 요청 접수 시각(12:59:58.999)에서 마감까지 남은 1ms를 실제로 대기
                TimeUnit.MILLISECONDS.sleep(TIMEOUT_START_DELAY_MILLIS);
                timelineRecorder.recordTimeoutStarted();

                // 실제 운영 코드의 조회 + Dirty Checking 타임아웃 처리 실행
                Throwable failure = catchThrowable(
                        () -> dailyBattleService.timeoutInProgress(fixture.battleId()));

                // 서비스가 정상 반환했다면 TIME_OVER Dirty Checking 커밋까지 완료된 상태
                if (failure == null) {
                    timelineRecorder.recordTimeoutCommitted();
                }
                return failure;
            });

            // 3. 제출 요청 100개를 별도 실행기에 등록하고 동일한 출발선에서 대기
            for (StressParticipant participant : fixture.participants()) {
                submissionTasks.add(submissionExecutor.submit(() -> {
                    // 현재 제출 작업이 출발 준비를 마쳤다고 알림
                    workersReady.countDown();

                    // 타임아웃 작업까지 모두 준비될 때까지 실제 제출 서비스 호출 정지
                    await(startRace, "stress test did not start");

                    try {
                        // 외부 트랜잭션을 먼저 시작해 시나리오 1과 2의 트랜잭션 시작 시점을 정확히 기록
                        BattleSubmissionResponseDto response = transactionTemplate.execute(status -> {
                            timelineRecorder.recordSubmissionStarted(participant.participationId());

                            // 모든 사용자는 마감 전 동일한 submittedAt으로 제출 시도
                            return dailyBattleService.battleSubmission(
                                    submissionRequest(fixture.battleId()),
                                    fixture.battleId(),
                                    participant.userId(),
                                    fixture.submittedAt());
                        });

                        // transactionTemplate 반환 시 제출 트랜잭션 커밋까지 끝난 상태
                        timelineRecorder.recordSubmissionCommitted(participant.participationId());
                        return SubmissionAttempt.succeeded(
                                participant.participationId(),
                                response.participationStatus());
                    } catch (Exception exception) {
                        // 조건부 UPDATE 0건 또는 DB 동시성 예외를 사용자별 결과로 보관
                        return SubmissionAttempt.failed(participant.participationId(), exception);
                    }
                }));
            }

            // 4. 제출 100개와 타임아웃 1개가 전부 준비될 때까지 테스트 스레드 대기
            await(workersReady, "stress workers were not ready");

            // 5. 제출 100개는 즉시 시작하고, 타임아웃은 이 시점부터 100ms 뒤에 시작
            timelineRecorder.recordRaceStarted();
            startRace.countDown();

            // 6. 타임아웃과 모든 제출 작업이 종료될 때까지 기다려 결과 수집
            timeoutFailure = get(timeoutTask, STRESS_WAIT_SECONDS);
            for (Future<SubmissionAttempt> submissionTask : submissionTasks) {
                attempts.add(get(submissionTask, STRESS_WAIT_SECONDS));
            }
        } finally {
            // 테스트 실패 시 대기 중인 작업이 남지 않도록 래치와 두 실행기 모두 정리
            startRace.countDown();
            submissionExecutor.shutdownNow();
            timeoutExecutor.shutdownNow();
            assertThat(submissionExecutor.awaitTermination(STRESS_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(timeoutExecutor.awaitTermination(STRESS_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        // 7. 모든 트랜잭션이 끝난 뒤 100명의 최종 DB 상태를 한 번에 다시 조회
        List<Long> participationIds = fixture.participants().stream()
                .map(StressParticipant::participationId)
                .toList();
        Map<Long, BattleParticipation> persistedById = battleParticipationRepository
                .findAllById(participationIds)
                .stream()
                .collect(Collectors.toMap(BattleParticipation::getParticipationId, Function.identity()));

        // 8. 제출 API 호출 자체의 성공/실패 인원 집계
        long apiSucceeded = attempts.stream().filter(SubmissionAttempt::succeeded).count();
        long apiFailed = attempts.size() - apiSucceeded;

        // 9. API 응답과 관계없이 DB에 남은 최종 상태 집계
        long finalSubmitted = persistedById.values().stream()
                .filter(participation -> participation.getParticipationStatus() == SUBMITTED)
                .count();
        long finalTimeOver = persistedById.values().stream()
                .filter(participation -> participation.getParticipationStatus() == TIME_OVER)
                .count();
        int maximumPoolSize = maximumPoolSize();
        long committedBeforeTimeoutRead = timelineRecorder.submissionsCommittedBeforeTimeoutRead();
        long timeoutSelectedCount = timelineRecorder.timeoutSelectedCount();

        // API는 성공했지만 최종 상태가 TIME_OVER이면 stale Dirty Checking 덮어쓰기 문제
        long succeededButTimeOver = attempts.stream()
                .filter(SubmissionAttempt::succeeded)
                .filter(attempt -> persistedById.get(attempt.participationId())
                        .getParticipationStatus() == TIME_OVER)
                .count();

        // 시나리오 1: 타임아웃 조회 완료 후 제출 트랜잭션이 시작되고, 제출 커밋 뒤 타임아웃 커밋
        long scenarioOne = attempts.stream()
                .filter(SubmissionAttempt::succeeded)
                .filter(attempt -> persistedById.get(attempt.participationId())
                        .getParticipationStatus() == TIME_OVER)
                .filter(attempt -> timelineRecorder.isScenarioOne(attempt.participationId()))
                .count();

        // 시나리오 2: 제출 트랜잭션 시작 후 조건부 UPDATE 전에 타임아웃이 조회하고 나중에 덮어쓰기
        long scenarioTwo = attempts.stream()
                .filter(SubmissionAttempt::succeeded)
                .filter(attempt -> persistedById.get(attempt.participationId())
                        .getParticipationStatus() == TIME_OVER)
                .filter(attempt -> timelineRecorder.isScenarioTwo(attempt.participationId()))
                .count();

        // 마감 전 요청이 실패했고 최종 상태가 TIME_OVER이면 타임아웃 선커밋 문제
        long failedAndTimeOver = attempts.stream()
                .filter(attempt -> !attempt.succeeded())
                .filter(attempt -> attempt.failure() instanceof BusinessException businessException
                        && "Battle_already_participated".equals(businessException.getMessage()))
                .filter(attempt -> persistedById.get(attempt.participationId())
                        .getParticipationStatus() == TIME_OVER)
                .count();

        // 시나리오 3: 타임아웃 커밋이 먼저 끝난 뒤 제출 조건부 UPDATE가 실행되어 0건 반환
        long scenarioThree = attempts.stream()
                .filter(attempt -> !attempt.succeeded())
                .filter(attempt -> attempt.failure() instanceof BusinessException businessException
                        && "Battle_already_participated".equals(businessException.getMessage()))
                .filter(attempt -> persistedById.get(attempt.participationId())
                        .getParticipationStatus() == TIME_OVER)
                .filter(attempt -> timelineRecorder.isScenarioThree(attempt.participationId()))
                .count();

        // API도 성공하고 최종 DB 상태도 SUBMITTED인 사용자만 정상 제출로 분류
        long normallySubmitted = attempts.stream()
                .filter(SubmissionAttempt::succeeded)
                .filter(attempt -> persistedById.get(attempt.participationId())
                        .getParticipationStatus() == SUBMITTED)
                .count();

        // 두 동시성 문제 유형을 합쳐 전체 문제 발생 사용자 수 계산
        long problemUsers = succeededButTimeOver + failedAndTimeOver;
        long otherOverwriteOrder = succeededButTimeOver - scenarioOne - scenarioTwo;
        long otherTimeoutRejectionOrder = failedAndTimeOver - scenarioThree;

        // 10. API 성공 1건당 답안 3건이 실제 DB에 저장됐는지 참여자별로 집계
        int savedSubmissionCount = transactionTemplate.execute(status -> fixture.participants().stream()
                .mapToInt(participant -> battleSubmissionRepository
                        .findByParticipation_participationId(participant.participationId())
                        .size())
                .sum());

        long otherFinalStatus = STRESS_USER_COUNT - finalSubmitted - finalTimeOver;
        long otherSubmissionFailures = apiFailed - failedAndTimeOver;

        // 11. 문제 발생 인원은 매번 달라질 수 있으므로 고정 숫자가 아닌 합계와 저장 일관성만 검증
        assertThat(attempts).hasSize(STRESS_USER_COUNT);
        assertThat(persistedById).hasSize(STRESS_USER_COUNT);
        assertThat(apiSucceeded + apiFailed).isEqualTo(STRESS_USER_COUNT);
        assertThat(finalSubmitted + finalTimeOver + otherFinalStatus).isEqualTo(STRESS_USER_COUNT);
        assertThat((long) savedSubmissionCount).isEqualTo(apiSucceeded * 3);
        assertThat(scenarioOne + scenarioTwo + otherOverwriteOrder)
                .isEqualTo(succeededButTimeOver);
        assertThat(scenarioThree + otherTimeoutRejectionOrder)
                .isEqualTo(failedAndTimeOver);
        if (timeoutFailure == null && timeoutSelectedCount >= 0L) {
            // 타임아웃이 실제 조회한 참여자는 이후 제출로 복구되지 않으므로 최종 TIME_OVER 수와 같아야 함
            assertThat(finalTimeOver).isEqualTo(timeoutSelectedCount);
        }

        // 12. 실행 환경에서 실제로 몇 명에게 문제가 발생했는지 콘솔에 출력
        log.info("""

                ================== 100명 동시성 관찰 결과 ==================
                요청 접수 시각                         : {}
                실제 타임아웃 시작 지연                : {}ms
                DB 최대 커넥션 풀 크기                 : {}개
                타임아웃 SELECT 전 제출 커밋           : {}명
                타임아웃이 조회한 IN_PROGRESS          : {}명
                1. 전체 제출 요청                      : {}명
                2. 정상 제출 완료                      : {}명
                3. 문제 발생 사용자 합계               : {}명
                4. 제출 성공 후 TIME_OVER 덮어쓰기     : {}명
                   4-1. 재현 시나리오 1                : {}명
                   4-2. 재현 시나리오 2                : {}명
                   4-3. 기타 덮어쓰기 순서             : {}명
                5. 타임아웃 선커밋 후 제출 실패        : {}명
                   5-1. 재현 시나리오 3                : {}명
                   5-2. 기타 제출 거절 순서             : {}명
                6. 제출 API 성공                       : {}명
                7. 제출 API 실패                       : {}명
                8. 기타 제출 실패                      : {}명
                9. 제출 답안 저장                      : {}건
                10. 최종 SUBMITTED                     : {}명
                11. 최종 TIME_OVER                     : {}명
                12. 기타 최종 상태                     : {}명
                13. 타임아웃 워커 예외                 : {}
                ===========================================================
                """,
                fixture.submittedAt().atZone(KOREA_ZONE),
                timelineRecorder.timeoutStartDelayMillis(),
                maximumPoolSize,
                committedBeforeTimeoutRead,
                timeoutSelectedCount,
                STRESS_USER_COUNT,
                normallySubmitted,
                problemUsers,
                succeededButTimeOver,
                scenarioOne,
                scenarioTwo,
                otherOverwriteOrder,
                failedAndTimeOver,
                scenarioThree,
                otherTimeoutRejectionOrder,
                apiSucceeded,
                apiFailed,
                otherSubmissionFailures,
                savedSubmissionCount,
                finalSubmitted,
                finalTimeOver,
                otherFinalStatus,
                describe(timeoutFailure));
    }

    private void assertRaceWasReproduced(
            String scenario,
            Fixture fixture,
            BattleSubmissionResponseDto response) {
        // Then: 모든 트랜잭션이 끝난 뒤 별도 조회로 DB 최종 상태 확인
        BattleParticipation persisted = battleParticipationRepository
                .findById(fixture.participationId())
                .orElseThrow();

        // 다른 테스트 데이터와 섞이지 않도록 현재 참여자의 제출 답안만 집계
        int savedSubmissionCount = battleSubmissionRepository
                .findByParticipation_participationId(fixture.participationId())
                .size();

        // 제출 API는 성공했고 답안도 모두 저장됐는지 확인
        assertThat(response.participationStatus()).isEqualTo(SUBMITTED);
        assertThat(savedSubmissionCount).isEqualTo(3);

        // 하지만 타임아웃 Dirty Checking이 제출 결과를 덮어썼는지 확인
        assertThat(persisted.getParticipationStatus()).isEqualTo(TIME_OVER);
        assertThat(persisted.getSubmittedAt()).isNull();

        // 콘솔에서 재현 결과를 바로 확인할 수 있도록 핵심 값 출력
        log.info("""

                ==================== 동시성 테스트 결과 ====================
                시나리오              : {}
                1. 제출 API 응답 상태 : {}
                2. 제출 답안 저장     : {}건
                3. DB 최종 참여 상태  : {}
                4. DB 최종 제출 시각  : {}
                ===========================================================
                """,
                scenario,
                response.participationStatus(),
                savedSubmissionCount,
                persisted.getParticipationStatus(),
                persisted.getSubmittedAt());
    }

    private Fixture createFixture(int dayOffset) {
        // 작업 스레드가 조회할 수 있도록 테스트 데이터는 별도 트랜잭션으로 먼저 커밋
        return transactionTemplate.execute(status -> {
            LocalDate battleDate = BASE_BATTLE_DATE.plusDays(dayOffset);
            Instant submittedAt = battleDate.atTime(12, 55).atZone(KOREA_ZONE).toInstant();

            User user = userRepository.save(new User("concurrency-user-" + dayOffset, null));
            DailyBattle battle = dailyBattleRepository.save(new DailyBattle(
                    battleDate,
                    Category.ARRAY,
                    "concurrency battle " + dayOffset,
                    "reproduce submission and timeout race"));

            for (int caseNumber = 1; caseNumber <= 3; caseNumber++) {
                battleCaseRepository.save(new BattleCase(
                        battle,
                        "input-" + caseNumber,
                        "output-" + caseNumber,
                        caseNumber));
            }

            BattleParticipation participation = battleParticipationRepository.save(
                    new BattleParticipation(battle.getBattleId(), user));
            return new Fixture(
                    battle.getBattleId(),
                    user.getId(),
                    participation.getParticipationId(),
                    submittedAt);
        });
    }

    private StressFixture createStressFixture(int dayOffset, int participantCount) {
        // 작업 스레드가 즉시 조회할 수 있도록 배틀, 문제, 참여자를 한 트랜잭션에서 먼저 커밋
        return transactionTemplate.execute(status -> {
            LocalDate battleDate = BASE_BATTLE_DATE.plusDays(dayOffset);

            // 서비스의 제출 시간 검증을 통과하는 마감 1ms 전 시각 생성
            Instant submittedAt = battleDate
                    .atTime(12, 59, 58, 999_000_000)
                    .atZone(KOREA_ZONE)
                    .toInstant();

            // 100명이 공통으로 참여할 배틀 1개 생성
            DailyBattle battle = dailyBattleRepository.save(new DailyBattle(
                    battleDate,
                    Category.ARRAY,
                    "100-user concurrency battle",
                    "observe submissions racing with timeout"));

            // 모든 제출 요청이 저장할 배틀 문제 3개 생성
            for (int caseNumber = 1; caseNumber <= 3; caseNumber++) {
                battleCaseRepository.save(new BattleCase(
                        battle,
                        "stress-input-" + caseNumber,
                        "stress-output-" + caseNumber,
                        caseNumber));
            }

            // 사용자와 IN_PROGRESS 참여 정보를 요청 수만큼 생성
            List<StressParticipant> participants = new ArrayList<>(participantCount);
            for (int index = 1; index <= participantCount; index++) {
                User user = userRepository.save(new User("stress-user-" + dayOffset + "-" + index, null));
                BattleParticipation participation = battleParticipationRepository.save(
                        new BattleParticipation(battle.getBattleId(), user));
                participants.add(new StressParticipant(user.getId(), participation.getParticipationId()));
            }

            return new StressFixture(battle.getBattleId(), submittedAt, List.copyOf(participants));
        });
    }

    private DailyBattleSubmissionRequestDto submissionRequest(Long battleId) {
        return new DailyBattleSubmissionRequestDto(
                battleId,
                List.of(
                        new DailyBattleSubmissionRequestDto.Answer(1, "answer-1"),
                        new DailyBattleSubmissionRequestDto.Answer(2, "answer-2"),
                        new DailyBattleSubmissionRequestDto.Answer(3, "answer-3")));
    }

    private static void await(CountDownLatch latch, String failureMessage) {
        try {
            // 정해진 시간 안에 신호를 받지 못하면 테스트 실패 처리
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as(failureMessage)
                    .isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failureMessage, exception);
        }
    }

    private static <T> T get(Future<T> future) throws Exception {
        return get(future, WAIT_SECONDS);
    }

    private static <T> T get(Future<T> future, int waitSeconds) throws Exception {
        try {
            // 작업 스레드의 예외와 무한 대기를 테스트 스레드에서 감지
            return future.get(waitSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "없음";
        }
        return throwable.getClass().getSimpleName() + " (" + throwable.getMessage() + ")";
    }

    private int maximumPoolSize() {
        try {
            // 실제 테스트에서 사용 중인 Hikari 설정을 읽어 마지막 묶음 크기 가설 확인
            return dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
        } catch (SQLException | RuntimeException exception) {
            // DataSource가 다른 구현으로 감싸져 있으면 관찰 불가 값으로 출력
            return -1;
        }
    }

    private record Fixture(Long battleId, Long userId, Long participationId, Instant submittedAt) {
    }

    private record StressFixture(
            Long battleId,
            Instant submittedAt,
            List<StressParticipant> participants) {
    }

    private record StressParticipant(Long userId, Long participationId) {
    }

    private record SubmissionAttempt(
            Long participationId,
            boolean succeeded,
            ParticipationStatus responseStatus,
            Throwable failure) {

        private static SubmissionAttempt succeeded(Long participationId, ParticipationStatus responseStatus) {
            return new SubmissionAttempt(participationId, true, responseStatus, null);
        }

        private static SubmissionAttempt failed(Long participationId, Throwable failure) {
            return new SubmissionAttempt(participationId, false, null, failure);
        }
    }

    /**
     * 운영 코드는 건드리지 않고 테스트에서만 주요 이벤트의 발생 순서를 기록한다.
     * System.nanoTime()은 서로 다른 스레드 사이에서도 동일 JVM 안의 선후 비교에 사용할 수 있다.
     */
    static final class ConcurrencyTimelineRecorder {

        private volatile Long observedBattleId;
        private final AtomicLong raceStartedAt = new AtomicLong();
        private final AtomicLong timeoutStartedAt = new AtomicLong();
        private final AtomicLong timeoutReadStartedAt = new AtomicLong();
        private final AtomicLong timeoutReadCompletedAt = new AtomicLong();
        private final AtomicLong timeoutSelectedCount = new AtomicLong(-1L);
        private final AtomicLong timeoutCommittedAt = new AtomicLong();
        private final ConcurrentMap<Long, Long> submissionStartedAt = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, Long> conditionalUpdateReachedAt = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, Long> submissionCommittedAt = new ConcurrentHashMap<>();

        void arm(Long battleId) {
            reset();
            observedBattleId = battleId;
        }

        void recordSubmissionStarted(Long participationId) {
            if (observedBattleId != null) {
                submissionStartedAt.putIfAbsent(participationId, System.nanoTime());
            }
        }

        void recordRaceStarted() {
            if (observedBattleId != null) {
                raceStartedAt.compareAndSet(0L, System.nanoTime());
            }
        }

        void recordTimeoutStarted() {
            if (observedBattleId != null) {
                timeoutStartedAt.compareAndSet(0L, System.nanoTime());
            }
        }

        void recordTimeoutReadStarted(Long battleId) {
            if (battleId.equals(observedBattleId)) {
                timeoutReadStartedAt.compareAndSet(0L, System.nanoTime());
            }
        }

        void recordConditionalUpdateReached(Long participationId) {
            if (observedBattleId != null) {
                conditionalUpdateReachedAt.putIfAbsent(participationId, System.nanoTime());
            }
        }

        void recordSubmissionCommitted(Long participationId) {
            if (observedBattleId != null) {
                submissionCommittedAt.putIfAbsent(participationId, System.nanoTime());
            }
        }

        void recordTimeoutReadCompleted(Long battleId, int selectedCount) {
            if (battleId.equals(observedBattleId)) {
                timeoutReadCompletedAt.compareAndSet(0L, System.nanoTime());
                timeoutSelectedCount.compareAndSet(-1L, selectedCount);
            }
        }

        void recordTimeoutCommitted() {
            if (observedBattleId != null) {
                timeoutCommittedAt.compareAndSet(0L, System.nanoTime());
            }
        }

        boolean isScenarioOne(Long participationId) {
            Long submissionStarted = submissionStartedAt.get(participationId);
            Long submissionCommitted = submissionCommittedAt.get(participationId);
            long timeoutReadCompleted = timeoutReadCompletedAt.get();
            long timeoutCommitted = timeoutCommittedAt.get();

            return submissionStarted != null
                    && submissionCommitted != null
                    && timeoutReadCompleted > 0L
                    && timeoutCommitted > 0L
                    && timeoutReadCompleted < submissionStarted
                    && submissionCommitted < timeoutCommitted;
        }

        boolean isScenarioTwo(Long participationId) {
            Long submissionStarted = submissionStartedAt.get(participationId);
            Long conditionalUpdateReached = conditionalUpdateReachedAt.get(participationId);
            Long submissionCommitted = submissionCommittedAt.get(participationId);
            long timeoutReadCompleted = timeoutReadCompletedAt.get();
            long timeoutCommitted = timeoutCommittedAt.get();

            return submissionStarted != null
                    && conditionalUpdateReached != null
                    && submissionCommitted != null
                    && timeoutReadCompleted > 0L
                    && timeoutCommitted > 0L
                    && submissionStarted < timeoutReadCompleted
                    && timeoutReadCompleted < conditionalUpdateReached
                    && submissionCommitted < timeoutCommitted;
        }

        boolean isScenarioThree(Long participationId) {
            Long conditionalUpdateReached = conditionalUpdateReachedAt.get(participationId);
            long timeoutCommitted = timeoutCommittedAt.get();

            return conditionalUpdateReached != null
                    && timeoutCommitted > 0L
                    && timeoutCommitted < conditionalUpdateReached;
        }

        long timeoutStartDelayMillis() {
            long raceStarted = raceStartedAt.get();
            long timeoutStarted = timeoutStartedAt.get();
            if (raceStarted == 0L || timeoutStarted == 0L) {
                return -1L;
            }
            return TimeUnit.NANOSECONDS.toMillis(timeoutStarted - raceStarted);
        }

        long submissionsCommittedBeforeTimeoutRead() {
            long timeoutReadStarted = timeoutReadStartedAt.get();
            if (timeoutReadStarted == 0L) {
                return -1L;
            }
            return submissionCommittedAt.values().stream()
                    .filter(committedAt -> committedAt < timeoutReadStarted)
                    .count();
        }

        long timeoutSelectedCount() {
            return timeoutSelectedCount.get();
        }

        void reset() {
            observedBattleId = null;
            raceStartedAt.set(0L);
            timeoutStartedAt.set(0L);
            timeoutReadStartedAt.set(0L);
            timeoutReadCompletedAt.set(0L);
            timeoutSelectedCount.set(-1L);
            timeoutCommittedAt.set(0L);
            submissionStartedAt.clear();
            conditionalUpdateReachedAt.clear();
            submissionCommittedAt.clear();
        }
    }

    /**
     * 운영 repository 프록시 바깥에 테스트 전용 프록시를 한 겹 추가해
     * updateStatusIfMatch() 실행 직전에만 제출 스레드를 멈춘다.
     */
    static final class ConditionalUpdateGate {

        private volatile CountDownLatch updateReached = new CountDownLatch(0);
        private volatile CountDownLatch allowUpdate = new CountDownLatch(0);

        void arm(CountDownLatch updateReached, CountDownLatch allowUpdate) {
            this.updateReached = updateReached;
            this.allowUpdate = allowUpdate;
        }

        void pauseBeforeUpdate() {
            CountDownLatch currentUpdateReached = updateReached;
            CountDownLatch currentAllowUpdate = allowUpdate;

            // 타임아웃 스레드에 제출이 조건부 UPDATE 직전까지 왔음을 알림
            currentUpdateReached.countDown();

            // 타임아웃 스레드가 IN_PROGRESS 조회를 마칠 때까지 실제 UPDATE 대기
            await(currentAllowUpdate, "timeout transaction did not finish its IN_PROGRESS query");
        }

        void release() {
            allowUpdate.countDown();
            updateReached = new CountDownLatch(0);
            allowUpdate = new CountDownLatch(0);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConditionalUpdateGateConfig {

        @Bean
        ConditionalUpdateGate conditionalUpdateGate() {
            return new ConditionalUpdateGate();
        }

        @Bean
        ConcurrencyTimelineRecorder concurrencyTimelineRecorder() {
            return new ConcurrencyTimelineRecorder();
        }

        @Bean
        static BeanPostProcessor conditionalUpdateGatePostProcessor(
                ConditionalUpdateGate gate,
                ConcurrencyTimelineRecorder timelineRecorder) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof BattleParticipationRepository)) {
                        return bean;
                    }

                    ProxyFactory proxyFactory = new ProxyFactory();
                    proxyFactory.setTarget(bean);
                    proxyFactory.setInterfaces(BattleParticipationRepository.class);
                    proxyFactory.addAdvice((MethodInterceptor) invocation -> {
                        String methodName = invocation.getMethod().getName();

                        if (methodName.equals("updateStatusIfMatch")) {
                            // 조건부 UPDATE SQL 실행 직전의 참여자별 시각 기록
                            timelineRecorder.recordConditionalUpdateReached(
                                    (Long) invocation.getArguments()[0]);
                            gate.pauseBeforeUpdate();
                        }

                        if (methodName.equals("findAllByDailyBattleIdAndParticipationStatus")) {
                            // 타임아웃 대상 SELECT를 호출하기 직전의 시각 기록
                            timelineRecorder.recordTimeoutReadStarted(
                                    (Long) invocation.getArguments()[0]);
                        }

                        Object result = invocation.proceed();

                        if (methodName.equals("findAllByDailyBattleIdAndParticipationStatus")) {
                            // 타임아웃 대상 SELECT가 반환된 시각과 조회된 IN_PROGRESS 수 기록
                            int selectedCount = result instanceof List<?> list ? list.size() : -1;
                            timelineRecorder.recordTimeoutReadCompleted(
                                    (Long) invocation.getArguments()[0],
                                    selectedCount);
                        }
                        return result;
                    });
                    return proxyFactory.getProxy(bean.getClass().getClassLoader());
                }
            };
        }
    }
}
