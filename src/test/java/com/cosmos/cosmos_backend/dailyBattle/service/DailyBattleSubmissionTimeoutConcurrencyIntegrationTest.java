package com.cosmos.cosmos_backend.dailyBattle.service;

import static com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus.IN_PROGRESS;
import static com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus.SUBMITTED;
import static com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus.TIME_OVER;
import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.Category;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    // 배틀 참여 가능 시간인 한국 시각 12:55로 제출 시각 고정
    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-21T03:55:00Z");

    // 동시성 테스트가 교착 상태로 무한 대기하지 않도록 최대 대기 시간 제한
    private static final int WAIT_SECONDS = 10;

    @Autowired
    private DailyBattleService dailyBattleService;

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

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() throws InterruptedException {
        // 테스트 실패 시 조건부 UPDATE 앞에서 대기 중인 스레드가 남지 않도록 게이트 해제
        conditionalUpdateGate.release();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void submissionCommittedAfterTimeoutReadIsOverwrittenByTimeoutDirtyChecking() throws Exception {
        // Given: IN_PROGRESS 참여자 1명과 제출할 배틀 문제 3개 생성
        Fixture fixture = createFixture();

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
                    SUBMITTED_AT);

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

        // Then: 모든 트랜잭션이 끝난 뒤 별도 조회로 DB 최종 상태 확인
        BattleParticipation persisted = battleParticipationRepository
                .findById(fixture.participationId())
                .orElseThrow();

        long savedSubmissionCount = battleSubmissionRepository.count();

        // 제출 API는 성공했고 답안도 모두 저장됐는지 확인
        assertThat(response.participationStatus()).isEqualTo(SUBMITTED);
        assertThat(savedSubmissionCount).isEqualTo(3);

        // 하지만 타임아웃 Dirty Checking이 제출 결과를 덮어썼는지 확인
        assertThat(persisted.getParticipationStatus()).isEqualTo(TIME_OVER);
        assertThat(persisted.getSubmittedAt()).isNull();

        // 콘솔에서 재현 결과를 바로 확인할 수 있도록 핵심 값 출력
        log.info("""

                ==================== 동시성 테스트 결과 ====================
                1. 제출 API 응답 상태 : {}
                2. 제출 답안 저장     : {}건
                3. DB 최종 참여 상태  : {}
                4. DB 최종 제출 시각  : {}
                ===========================================================
                """,
                response.participationStatus(),
                savedSubmissionCount,
                persisted.getParticipationStatus(),
                persisted.getSubmittedAt());
    }

    private Fixture createFixture() {
        // 작업 스레드가 조회할 수 있도록 테스트 데이터는 별도 트랜잭션으로 먼저 커밋
        return transactionTemplate.execute(status -> {
            User user = userRepository.save(new User("concurrency-user", null));
            DailyBattle battle = dailyBattleRepository.save(new DailyBattle(
                    LocalDate.of(2026, 10, 21),
                    Category.ARRAY,
                    "concurrency battle",
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
            return new Fixture(battle.getBattleId(), user.getId(), participation.getParticipationId());
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
        try {
            // 작업 스레드의 예외와 무한 대기를 테스트 스레드에서 감지
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private record Fixture(Long battleId, Long userId, Long participationId) {
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
        static BeanPostProcessor conditionalUpdateGatePostProcessor(ConditionalUpdateGate gate) {
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
                        if (invocation.getMethod().getName().equals("updateStatusIfMatch")) {
                            gate.pauseBeforeUpdate();
                        }
                        return invocation.proceed();
                    });
                    return proxyFactory.getProxy(bean.getClass().getClassLoader());
                }
            };
        }
    }
}
