package com.cosmos.cosmos_backend.dailyBattle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.GradingPreempt;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.GradingResult;
import com.cosmos.cosmos_backend.dailyBattle.metrics.BattleMetrics;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.global.config.JpaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        "logging.level.com.cosmos.cosmos_backend.dailyBattle.service.BattleGradingIntegrationTest=INFO"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        BattleGradingService.class,
        BattleResultSaveService.class,
        BattleMetrics.class,
        JpaConfig.class,
        BattleGradingIntegrationTest.TestSupportConfig.class
})
class BattleGradingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BattleGradingIntegrationTest.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int WAIT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private BattleGradingService battleGradingService;

    @Autowired
    private BattleResultSaveService battleResultSaveService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyBattleRepository dailyBattleRepository;

    @Autowired
    private BattleCaseRepository battleCaseRepository;

    @Autowired
    private BattleParticipationRepository participationRepository;

    @Autowired
    private BattleSubmissionRepository submissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    private Long participationId;
    private Long submissionId;
    private Long battleId;

    @BeforeEach
    void setUp() {
        //given: 각 테스트가 독립적으로 실행되도록 기존 데이터를 제거
        transactionTemplate.executeWithoutResult(status -> {
            submissionRepository.deleteAllInBatch();
            participationRepository.deleteAllInBatch();
            battleCaseRepository.deleteAllInBatch();
            dailyBattleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            //given: 채점할 SUBMITTED 참여와 정답 제출 데이터를 생성
            User user = userRepository.save(new User("grading-worker-user", null));
            DailyBattle battle = dailyBattleRepository.save(new DailyBattle(
                    LocalDate.of(2099, 1, 1),
                    Category.ARRAY,
                    "grading worker integration battle",
                    "lease and stale worker integration test"));
            battleId = battle.getBattleId();
            BattleCase battleCase = battleCaseRepository.save(new BattleCase(
                    battle,
                    "1 2",
                    "3",
                    1));

            BattleParticipation participation = new BattleParticipation(battle.getBattleId(), user);
            participation.markSubmit(Instant.now(clock));
            participation = participationRepository.save(participation);

            BattleSubmission submission = submissionRepository.save(new BattleSubmission(
                    participation,
                    battleCase,
                    "3"));

            participationId = participation.getParticipationId();
            submissionId = submission.getBattleSubmissionId();
        });
    }

    @Test
    void firstClaimDoesNotIncreaseLeaseRetryCount() {
        //given: SUBMITTED 상태의 채점 작업이 존재

        //when: 워커가 작업을 최초로 선점
        GradingPreempt claim = battleGradingService.preemptSubmission(battleId).orElseThrow();

        //then: GRADING 상태와 임대 토큰이 설정되고 재선점 횟수는 증가하지 않음
        BattleParticipation participation = findParticipation();

        assertThat(claim.participationId()).isEqualTo(participationId);
        assertThat(participation.getParticipationStatus()).isEqualTo(ParticipationStatus.GRADING);
        assertThat(participation.getLeaseRetryCount()).isZero();
        assertThat(participation.getLeaseToken()).isEqualTo(claim.leaseToken());
        assertThat(participation.getLeaseExpiresAt()).isNotNull();

        printTestResult("최초 작업 선점",
                "참여 ID          : " + participationId,
                "최종 상태        : " + participation.getParticipationStatus(),
                "임대 토큰        : " + participation.getLeaseToken(),
                "임대 만료 시각   : " + participation.getLeaseExpiresAt(),
                "재선점 횟수      : " + participation.getLeaseRetryCount());
    }

    @Test
    void workerClaimsOnlyTheRequestedBattleTask() {
        //given: 다른 배틀에 더 일찍 제출된 작업이 존재
        Long otherParticipationId = transactionTemplate.execute(status -> {
            User otherUser = userRepository.save(new User("other-battle-user", null));
            DailyBattle otherBattle = dailyBattleRepository.save(new DailyBattle(
                    LocalDate.of(2098, 12, 31),
                    Category.ARRAY,
                    "other grading worker integration battle",
                    "battle scope integration test"));

            BattleParticipation otherParticipation = new BattleParticipation(
                    otherBattle.getBattleId(),
                    otherUser
            );
            otherParticipation.markSubmit(clock.instant().minusSeconds(60));
            return participationRepository.save(otherParticipation).getParticipationId();
        });

        //when: 현재 배틀 ID로 작업 선점을 요청
        GradingPreempt claim = battleGradingService.preemptSubmission(battleId).orElseThrow();

        //then: 제출 시각이 더 이른 다른 배틀 작업이 아닌 요청한 배틀 작업만 선점
        BattleParticipation otherParticipation = participationRepository.findById(otherParticipationId).orElseThrow();

        assertThat(claim.participationId()).isEqualTo(participationId);
        assertThat(otherParticipation.getParticipationStatus()).isEqualTo(ParticipationStatus.SUBMITTED);

        printTestResult("배틀 범위가 제한된 작업 선점",
                "요청 배틀 ID      : " + battleId,
                "선점 참여 ID      : " + claim.participationId(),
                "다른 배틀 참여 ID : " + otherParticipationId,
                "다른 배틀 상태    : " + otherParticipation.getParticipationStatus());
    }

    @Test
    void expiredLeaseCanBeReclaimed() {
        //given: 한 워커가 작업을 선점한 뒤 임대 시간이 만료
        GradingPreempt firstClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();
        expireLease();

        //when: 다른 워커가 만료된 작업을 다시 선점
        GradingPreempt secondClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();

        //then: 새 임대 토큰이 발급되고 재선점 횟수가 1 증가
        BattleParticipation participation = findParticipation();

        assertThat(secondClaim.participationId()).isEqualTo(participationId);
        assertThat(secondClaim.leaseToken()).isNotEqualTo(firstClaim.leaseToken());
        assertThat(participation.getParticipationStatus()).isEqualTo(ParticipationStatus.GRADING);
        assertThat(participation.getLeaseRetryCount()).isEqualTo(1);
        assertThat(participation.getLeaseToken()).isEqualTo(secondClaim.leaseToken());

        printTestResult("만료된 작업 재선점",
                "참여 ID          : " + participationId,
                "이전 임대 토큰   : " + firstClaim.leaseToken(),
                "현재 임대 토큰   : " + secondClaim.leaseToken(),
                "최종 상태        : " + participation.getParticipationStatus(),
                "재선점 횟수      : " + participation.getLeaseRetryCount());
    }

    @Test
    void staleWorkerCannotSaveResultAfterLeaseIsReclaimed() {
        //given: 첫 워커의 임대가 만료되고 다른 워커가 새 토큰으로 재선점
        GradingPreempt staleClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();
        expireLease();
        GradingPreempt currentClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();

        //when: 첫 워커가 만료된 토큰으로 채점 결과 저장을 시도
        boolean saved = battleResultSaveService.saveGradedResult(
                participationId,
                staleClaim.leaseToken(),
                List.of(new GradingResult(submissionId, true)));

        //then: 저장이 거부되고 현재 워커의 임대 상태와 미채점 결과가 유지
        BattleParticipation participation = findParticipation();
        BattleSubmission submission = findSubmission();

        assertThat(saved).isFalse();
        assertThat(participation.getParticipationStatus()).isEqualTo(ParticipationStatus.GRADING);
        assertThat(participation.getLeaseToken()).isEqualTo(currentClaim.leaseToken());
        assertThat(participation.getAllCorrect()).isNull();
        assertThat(submission.getIsCorrect()).isNull();

        printTestResult("만료된 워커의 결과 저장 차단",
                "참여 ID          : " + participationId,
                "저장 성공 여부   : " + saved,
                "만료된 토큰      : " + staleClaim.leaseToken(),
                "현재 토큰        : " + currentClaim.leaseToken(),
                "최종 상태        : " + participation.getParticipationStatus(),
                "개별 채점 결과   : " + submission.getIsCorrect());
    }

    @Test
    void staleWorkerCannotChangeRetryStateAfterLeaseIsReclaimed() {
        //given: 첫 워커의 임대가 만료되고 다른 워커가 새 토큰으로 재선점
        GradingPreempt staleClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();
        expireLease();
        GradingPreempt currentClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();

        //when: 첫 워커가 만료된 토큰으로 채점 실패 처리를 시도
        battleGradingService.checkGradingFailed(participationId, staleClaim.leaseToken());

        //then: 현재 워커의 임대 상태와 일반 재시도 횟수가 변경되지 않음
        BattleParticipation participation = findParticipation();

        assertThat(participation.getParticipationStatus()).isEqualTo(ParticipationStatus.GRADING);
        assertThat(participation.getLeaseToken()).isEqualTo(currentClaim.leaseToken());
        assertThat(participation.getRetryCount()).isZero();
        assertThat(participation.getLeaseRetryCount()).isEqualTo(1);

        printTestResult("만료된 워커의 실패 처리 차단",
                "참여 ID          : " + participationId,
                "만료된 토큰      : " + staleClaim.leaseToken(),
                "현재 토큰        : " + currentClaim.leaseToken(),
                "최종 상태        : " + participation.getParticipationStatus(),
                "채점 재시도 횟수 : " + participation.getRetryCount(),
                "임대 재선점 횟수 : " + participation.getLeaseRetryCount());
    }

    @Test
    void taskFailsWhenExpiredLeaseExceedsThreeReclaims() {
        //given: 최초 선점 이후 임대 만료와 재선점이 3회 반복
        battleGradingService.preemptSubmission(battleId).orElseThrow();

        for (int retry = 1; retry <= 3; retry++) {
            expireLease();
            GradingPreempt reclaimed = battleGradingService.preemptSubmission(battleId).orElseThrow();

            assertThat(findParticipation().getLeaseRetryCount()).isEqualTo(retry);
            assertThat(reclaimed.participationId()).isEqualTo(participationId);
        }

        //when: 세 번째 재선점의 임대까지 만료된 뒤 추가 선점을 시도
        expireLease();
        Optional<GradingPreempt> fourthReclaim = battleGradingService.preemptSubmission(battleId);

        //then: 추가 선점은 거부되고 작업은 최종 실패 상태로
        BattleParticipation failed = findParticipation();

        assertThat(fourthReclaim).isEmpty();
        assertThat(failed.getParticipationStatus()).isEqualTo(ParticipationStatus.GRADING_FAILED);
        assertThat(failed.getLeaseRetryCount()).isEqualTo(3);

        printTestResult("임대 재선점 한도 초과",
                "참여 ID             : " + participationId,
                "네 번째 재선점 성공 : " + fourthReclaim.isPresent(),
                "최종 상태           : " + failed.getParticipationStatus(),
                "최종 재선점 횟수    : " + failed.getLeaseRetryCount());
    }

    @Test
    void currentWorkerCanGradeAndSaveResult() {
        //given: 현재 워커가 유효한 임대 토큰으로 작업을 선점
        GradingPreempt claim = battleGradingService.preemptSubmission(battleId).orElseThrow();

        //when: 현재 워커가 실제 채점과 결과 저장을 수행
        battleGradingService.battleGrade(
                participationId,
                claim.leaseToken(),
                Instant.now(clock));

        //then: 참여와 개별 답안이 모두 채점 완료 상태로 저장
        BattleParticipation participation = findParticipation();
        BattleSubmission submission = findSubmission();

        assertThat(participation.getParticipationStatus())
                .isEqualTo(ParticipationStatus.GRADING_COMPLETED);
        assertThat(participation.getAllCorrect()).isTrue();
        assertThat(submission.getIsCorrect()).isTrue();

        printTestResult("현재 워커의 정상 채점",
                "참여 ID          : " + participationId,
                "최종 상태        : " + participation.getParticipationStatus(),
                "전체 정답 여부   : " + participation.getAllCorrect(),
                "개별 정답 여부   : " + submission.getIsCorrect());
    }

    @Test
    void battleGradeCanUseLazyBattleCaseIdWithoutAnOuterTransaction() {
        //given: 테스트 메서드에는 외부 트랜잭션이 없고 BattleCase 연관관계는 초기화되지 않은 상태
        BattleSubmission detachedSubmission = findSubmission();

        assertThat(Hibernate.isInitialized(detachedSubmission.getBattleCase())).isFalse();

        //when: LAZY 프록시의 ID를 읽고 실제 채점 서비스를 실행
        assertThatCode(() -> detachedSubmission.getBattleCase().getCaseId())
                .doesNotThrowAnyException();

        GradingPreempt claim = battleGradingService.preemptSubmission(battleId).orElseThrow();
        assertThatCode(() -> battleGradingService.battleGrade(
                participationId,
                claim.leaseToken(),
                Instant.now(clock)))
                .doesNotThrowAnyException();

        //then: LazyInitializationException 없이 채점 결과가 정상 저장
        BattleParticipation participation = findParticipation();
        BattleSubmission gradedSubmission = findSubmission();

        assertThat(participation.getParticipationStatus())
                .isEqualTo(ParticipationStatus.GRADING_COMPLETED);
        assertThat(participation.getAllCorrect()).isTrue();
        assertThat(gradedSubmission.getIsCorrect()).isTrue();

        printTestResult("트랜잭션 밖 LAZY BattleCase ID 접근",
                "외부 트랜잭션        : 없음",
                "BattleCase 초기화 여부 : false (미초기화 상태)",
                "BattleCase ID 접근   : 성공",
                "채점 서비스 실행     : 성공",
                "최종 상태            : " + participation.getParticipationStatus());
    }

    @Test
    void twoWorkersCannotClaimTheSameTaskAtTheSameTime() throws Exception {
        //given: SUBMITTED 작업 1개와 동시에 선점을 시도할 워커 2개가 존재
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            Future<Optional<GradingPreempt>> firstWorker = executor.submit(() -> {
                workersReady.countDown();
                await(startTogether, "첫 번째 워커가 선점 시작 신호를 받지 못함");
                return battleGradingService.preemptSubmission(battleId);
            });
            Future<Optional<GradingPreempt>> secondWorker = executor.submit(() -> {
                workersReady.countDown();
                await(startTogether, "두 번째 워커가 선점 시작 신호를 받지 못함");
                return battleGradingService.preemptSubmission(battleId);
            });

            await(workersReady, "두 워커가 동시에 준비되지 못함");

            //when: 두 워커가 같은 순간에 작업 선점을 시도
            startTogether.countDown();
            Optional<GradingPreempt> firstClaim = get(firstWorker);
            Optional<GradingPreempt> secondClaim = get(secondWorker);

            //then: 두 워커 중 하나만 작업을 선점
            long claimedWorkerCount = List.of(firstClaim, secondClaim).stream()
                    .filter(Optional::isPresent)
                    .count();
            BattleParticipation participation = findParticipation();

            assertThat(claimedWorkerCount).isEqualTo(1);
            assertThat(participation.getParticipationStatus()).isEqualTo(ParticipationStatus.GRADING);

            printTestResult("두 워커의 동시 선점",
                    "첫 번째 워커 선점 : " + firstClaim.isPresent(),
                    "두 번째 워커 선점 : " + secondClaim.isPresent(),
                    "총 선점 워커 수   : " + claimedWorkerCount,
                    "최종 상태         : " + participation.getParticipationStatus());
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void anotherWorkerCannotReclaimWhilePreviousWorkerIsSavingResult() throws Exception {
        //given: 임대는 만료됐지만 이전 워커가 유효한 토큰으로 결과 저장을 시작
        GradingPreempt previousClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();
        expireLease();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch resultSavedWhileLockHeld = new CountDownLatch(1);
        CountDownLatch allowSaveTransactionCommit = new CountDownLatch(1);

        try {
            Future<Boolean> saveResult = executor.submit(() -> transactionTemplate.execute(status -> {
                boolean saved = battleResultSaveService.saveGradedResult(
                        participationId,
                        previousClaim.leaseToken(),
                        List.of(new GradingResult(submissionId, true)));

                //given: 외부 트랜잭션을 유지해 참여 행의 비관적 락을 잡은 상태로 대기
                resultSavedWhileLockHeld.countDown();
                await(allowSaveTransactionCommit, "결과 저장 트랜잭션 종료 신호를 받지 못함");
                return saved;
            }));

            await(resultSavedWhileLockHeld, "이전 워커가 결과 저장 락을 획득하지 못함");

            //when: 결과 저장 트랜잭션이 락을 보유한 동안 다른 워커가 재선점을 시도
            Future<Optional<GradingPreempt>> reclaim = executor.submit(
                    () -> battleGradingService.preemptSubmission(battleId));
            Optional<GradingPreempt> reclaimedWhileSaving = get(reclaim);

            allowSaveTransactionCommit.countDown();
            boolean saved = get(saveResult);

            //then: 다른 워커는 잠긴 작업을 건너뛰고 이전 워커의 결과만 반영
            BattleParticipation participation = findParticipation();
            BattleSubmission submission = findSubmission();

            assertThat(reclaimedWhileSaving).isEmpty();
            assertThat(saved).isTrue();
            assertThat(participation.getParticipationStatus())
                    .isEqualTo(ParticipationStatus.GRADING_COMPLETED);
            assertThat(submission.getIsCorrect()).isTrue();

            printTestResult("결과 저장 중 재선점 차단",
                    "결과 저장 성공     : " + saved,
                    "동시 재선점 성공   : " + reclaimedWhileSaving.isPresent(),
                    "최종 상태          : " + participation.getParticipationStatus(),
                    "개별 정답 여부     : " + submission.getIsCorrect());
        } finally {
            allowSaveTransactionCommit.countDown();
            shutdown(executor);
        }
    }

    @Test
    void taskFailsAfterThreeGradingFailures() {
        //given: 동일한 채점 작업에서 예외 후 실패 처리가 반복되는 상황
        for (int attempt = 1; attempt <= 3; attempt++) {
            GradingPreempt claim = battleGradingService.preemptSubmission(battleId).orElseThrow();

            //when: 워커가 채점 예외를 잡고 현재 임대 토큰으로 실패 처리
            battleGradingService.checkGradingFailed(participationId, claim.leaseToken());

            BattleParticipation participation = findParticipation();

            //then: 1·2회는 SUBMITTED로 복귀하고 3회째에는 최종 실패 처리
            assertThat(participation.getRetryCount()).isEqualTo(attempt);
            if (attempt < 3) {
                assertThat(participation.getParticipationStatus())
                        .isEqualTo(ParticipationStatus.SUBMITTED);
            } else {
                assertThat(participation.getParticipationStatus())
                        .isEqualTo(ParticipationStatus.GRADING_FAILED);
            }
        }

        BattleParticipation failed = findParticipation();
        printTestResult("채점 예외 3회 후 최종 실패",
                "채점 재시도 횟수 : " + failed.getRetryCount(),
                "임대 재선점 횟수 : " + failed.getLeaseRetryCount(),
                "최종 상태        : " + failed.getParticipationStatus());
    }

    @Test
    void gradingRetryCountAndLeaseRetryCountIncreaseIndependently() {
        //given: 한 번의 채점 예외로 retryCount만 증가한 작업
        GradingPreempt failedClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();
        battleGradingService.checkGradingFailed(participationId, failedClaim.leaseToken());

        BattleParticipation afterGradingFailure = findParticipation();
        assertThat(afterGradingFailure.getRetryCount()).isEqualTo(1);
        assertThat(afterGradingFailure.getLeaseRetryCount()).isZero();

        //when: 다시 선점된 작업의 임대가 만료되어 다른 워커가 재선점
        battleGradingService.preemptSubmission(battleId).orElseThrow();
        expireLease();
        battleGradingService.preemptSubmission(battleId).orElseThrow();

        //then: retryCount는 유지되고 leaseRetryCount만 증가
        BattleParticipation afterLeaseReclaim = findParticipation();

        assertThat(afterLeaseReclaim.getRetryCount()).isEqualTo(1);
        assertThat(afterLeaseReclaim.getLeaseRetryCount()).isEqualTo(1);

        printTestResult("채점 재시도와 임대 재선점 횟수 분리",
                "채점 재시도 횟수 : " + afterLeaseReclaim.getRetryCount(),
                "임대 재선점 횟수 : " + afterLeaseReclaim.getLeaseRetryCount(),
                "최종 상태        : " + afterLeaseReclaim.getParticipationStatus());
    }

    @Test
    void currentWorkerCanSaveAfterLeaseExpiresBeforeAnotherWorkerReclaims() {
        //given: 현재 워커의 임대 시간은 만료됐지만 아직 다른 워커가 재선점하지 않은 상태
        GradingPreempt currentClaim = battleGradingService.preemptSubmission(battleId).orElseThrow();
        expireLease();

        //when: 현재 토큰을 가진 워커가 결과 저장을 시도
        boolean saved = battleResultSaveService.saveGradedResult(
                participationId,
                currentClaim.leaseToken(),
                List.of(new GradingResult(submissionId, true)));

        //then: 토큰 소유권이 바뀌지 않았으므로 결과 저장에 성공
        BattleParticipation participation = findParticipation();
        BattleSubmission submission = findSubmission();

        assertThat(saved).isTrue();
        assertThat(participation.getParticipationStatus())
                .isEqualTo(ParticipationStatus.GRADING_COMPLETED);
        assertThat(submission.getIsCorrect()).isTrue();

        printTestResult("임대 만료 후 재선점 전 결과 저장",
                "임대 시간 상태     : 만료",
                "다른 워커 재선점   : 없음",
                "기존 토큰 저장 성공: " + saved,
                "최종 상태          : " + participation.getParticipationStatus());
    }

    private void expireLease() {
        jdbcTemplate.update("""
                UPDATE battle_participations
                   SET lease_expires_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 10 SECOND)
                 WHERE participation_id = ?
                """, participationId);
    }

    private BattleParticipation findParticipation() {
        return participationRepository.findById(participationId).orElseThrow();
    }

    private BattleSubmission findSubmission() {
        return submissionRepository.findById(submissionId).orElseThrow();
    }

    private void printTestResult(String scenario, String... details) {
        log.info("""

                ==================== 채점 워커 통합 테스트 결과 ====================
                시나리오 : {}
                {}
                ===================================================================
                """, scenario, String.join("\n", details));
    }

    private static void await(CountDownLatch latch, String failureMessage) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as(failureMessage)
                    .isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failureMessage, exception);
        }
    }

    private static <T> T get(Future<T> future) throws Exception {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfig {

        @Bean
        Clock clock() {
            return Clock.system(KOREA_ZONE);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
