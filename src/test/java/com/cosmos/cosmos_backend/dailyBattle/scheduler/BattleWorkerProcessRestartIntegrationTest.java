package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.LocalTime;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class BattleWorkerProcessRestartIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BattleWorkerProcessRestartIntegrationTest.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalTime OPEN_TIME = LocalTime.of(10, 0);
    private static final LocalTime SUBMISSION_DEADLINE = LocalTime.of(10, 10);
    private static final LocalTime RESULT_TIME = LocalTime.of(10, 11);

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TempDir
    Path tempDirectory;

    // Spring 컨텍스트 재시작 시 ApplicationReadyEvent만으로 만료된 작업이 복구되는지 확인하는 테스트
    @Test
    void applicationReadyEventRecoversExpiredTaskAfterApplicationContextRestart() throws InterruptedException {
        Long participationId;

        //given: 배틀 시작 전 시각으로 첫 번째 애플리케이션 컨텍스트 실행
        try (ConfigurableApplicationContext firstContext = startApplicationAt(OPEN_TIME.minusMinutes(1))) {
            clearBattleData(firstContext.getBean(DataSource.class));
            DailyBattleRepository dailyBattleRepository = firstContext.getBean(DailyBattleRepository.class);
            BattleCaseRepository battleCaseRepository = firstContext.getBean(BattleCaseRepository.class);
            BattleParticipationRepository participationRepository =
                    firstContext.getBean(BattleParticipationRepository.class);
            BattleSubmissionRepository submissionRepository =
                    firstContext.getBean(BattleSubmissionRepository.class);
            UserRepository userRepository = firstContext.getBean(UserRepository.class);

            DailyBattle battle = dailyBattleRepository.saveAndFlush(new DailyBattle(
                    TODAY,
                    Category.ARRAY,
                    "process restart integration battle",
                    "application ready event recovery integration test"
            ));
            BattleCase battleCase = battleCaseRepository.saveAndFlush(new BattleCase(
                    battle,
                    "1 2",
                    "3",
                    1
            ));
            User user = userRepository.saveAndFlush(new User("process-restart-user", null));
            BattleParticipation participation = new BattleParticipation(battle.getBattleId(), user);
            participation.markSubmit(firstContext.getBean(Clock.class).instant());
            participation = participationRepository.saveAndFlush(participation);
            submissionRepository.saveAndFlush(new BattleSubmission(participation, battleCase, "3"));

            //given: 첫 번째 서버의 워커가 작업을 선점한 상태에서 임대만 만료
            firstContext.getBean(BattleGradingService.class)
                    .preemptSubmission(battle.getBattleId())
                    .orElseThrow();
            participationId = participation.getParticipationId();
            expireLease(firstContext.getBean(DataSource.class), participationId);

            assertThat(participationRepository.findById(participationId).orElseThrow()
                    .getParticipationStatus()).isEqualTo(ParticipationStatus.GRADING);

            //when: 첫 번째 애플리케이션 컨텍스트 종료
        }

        //then: 종료 뒤에도 MySQL에는 만료된 GRADING 작업이 그대로 남아 있음
        assertParticipationStatusInDatabase(participationId, ParticipationStatus.GRADING);

        //when: 배틀 진행 중 시각으로 두 번째 애플리케이션 컨텍스트 재시작
        try (ConfigurableApplicationContext restartedContext = startApplicationAt(OPEN_TIME.plusMinutes(1))) {
            BattleParticipationRepository participationRepository =
                    restartedContext.getBean(BattleParticipationRepository.class);

            //then: recoverAfterRestart를 직접 호출하지 않아도 ApplicationReadyEvent가 실제 워커를 시작해 복구
            BattleParticipation recovered = waitForCompletedParticipation(
                    participationRepository,
                    participationId,
                    Duration.ofSeconds(5)
            );

            assertThat(recovered.getParticipationStatus())
                    .isEqualTo(ParticipationStatus.GRADING_COMPLETED);
            assertThat(recovered.getLeaseRetryCount()).isEqualTo(1);

            printTestResult(
                    "애플리케이션 컨텍스트 종료 및 재시작 복구",
                    "ApplicationReadyEvent 직접 발행 : 안 함",
                    "recoverAfterRestart 직접 호출    : 안 함",
                    "최종 작업 상태                  : " + recovered.getParticipationStatus(),
                    "임대 재선점 횟수                : " + recovered.getLeaseRetryCount()
            );
        }
    }

    // 실제 JVM을 강제 종료하고 새 JVM을 실행했을 때 만료된 GRADING 작업이 복구되는지 확인하는 테스트
    @Test
    void newJvmRecoversExpiredTaskAfterPreviousJvmIsForciblyTerminated() throws Exception {
        //given: 서로 다른 JVM 사이에서 참여 ID를 전달할 상태 파일과 자식 프로세스 로그 파일 준비
        Path stateFile = tempDirectory.resolve("participation-id.txt");
        Path firstProcessLog = tempDirectory.resolve("first-process.log");
        Path restartedProcessLog = tempDirectory.resolve("restarted-process.log");
        Process firstProcess = null;
        Process restartedProcess = null;

        try {
            //given: 첫 번째 실제 JVM 프로세스를 실행해 작업을 GRADING으로 선점
            firstProcess = startChildProcess("prepare", stateFile, firstProcessLog);
            assertThat(waitForFile(stateFile, Duration.ofSeconds(30)))
                    .withFailMessage(childFailureMessage("첫 번째 JVM 준비 실패", firstProcess, firstProcessLog))
                    .isTrue();
            Long participationId = Long.valueOf(Files.readString(stateFile).trim());

            //when: 첫 번째 JVM을 정상 종료 콜백 없이 강제 종료
            firstProcess.destroyForcibly();
            assertThat(firstProcess.waitFor(10, TimeUnit.SECONDS)).isTrue();

            //then: 프로세스가 사라져도 MySQL에는 선점 중이던 작업이 그대로 남음
            assertParticipationStatusInDatabase(participationId, ParticipationStatus.GRADING);

            //when: 두 번째 실제 JVM 프로세스 시작
            restartedProcess = startChildProcess("recover", stateFile, restartedProcessLog);

            //then: 새 JVM의 ApplicationReadyEvent가 복구 로직을 연결하고 정상 종료 코드 반환
            assertThat(restartedProcess.waitFor(30, TimeUnit.SECONDS))
                    .withFailMessage(childFailureMessage(
                            "재시작 JVM이 제한 시간 안에 끝나지 않음",
                            restartedProcess,
                            restartedProcessLog
                    ))
                    .isTrue();
            assertThat(restartedProcess.exitValue())
                    .withFailMessage(childFailureMessage(
                            "재시작 JVM 복구 실패",
                            restartedProcess,
                            restartedProcessLog
                    ))
                    .isZero();
            assertParticipationStatusInDatabase(participationId, ParticipationStatus.GRADING_COMPLETED);
            assertLeaseRetryCountInDatabase(participationId, 1);

            printTestResult(
                    "실제 JVM 프로세스 강제 종료 및 재기동",
                    "첫 번째 JVM 종료 방식 : destroyForcibly",
                    "두 번째 JVM 복구 방식 : ApplicationReadyEvent",
                    "최종 작업 상태        : " + ParticipationStatus.GRADING_COMPLETED,
                    "임대 재선점 횟수      : 1"
            );
        } finally {
            //then: 중간 검증이 실패해도 남아 있는 자식 JVM 프로세스 정리
            destroyIfAlive(firstProcess);
            destroyIfAlive(restartedProcess);
        }
    }

    private static ConfigurableApplicationContext startApplicationAt(LocalTime time) {
        SpringApplication application = new SpringApplication(RecoveryTestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(applicationProperties(time));
        return application.run();
    }

    private static Map<String, Object> applicationProperties(LocalTime time) {
        Map<String, Object> properties = commonApplicationProperties(time);
        properties.put("spring.datasource.url", MYSQL.getJdbcUrl());
        properties.put("spring.datasource.username", MYSQL.getUsername());
        properties.put("spring.datasource.password", MYSQL.getPassword());
        properties.put("spring.datasource.driver-class-name", MYSQL.getDriverClassName());
        return properties;
    }

    private static Map<String, Object> commonApplicationProperties(LocalTime time) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "WARN");
        properties.put("recovery.test.instant", TODAY.atTime(time).atZone(KOREA_ZONE).toInstant().toString());
        return properties;
    }

    private static void expireLease(DataSource dataSource, Long participationId) {
        new JdbcTemplate(dataSource).update("""
                UPDATE battle_participations
                   SET lease_expires_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 10 SECOND)
                 WHERE participation_id = ?
                """, participationId);
    }

    private static void clearBattleData(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM battle_submissions");
        jdbcTemplate.update("DELETE FROM battle_participations");
        jdbcTemplate.update("DELETE FROM battle_cases");
        jdbcTemplate.update("DELETE FROM daily_battles");
        jdbcTemplate.update("DELETE FROM users");
    }

    private void assertParticipationStatusInDatabase(
            Long participationId,
            ParticipationStatus expectedStatus
    ) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String status = jdbcTemplate.queryForObject(
                "SELECT participation_status FROM battle_participations WHERE participation_id = ?",
                String.class,
                participationId
        );

        assertThat(status).isEqualTo(expectedStatus.name());
    }

    private void assertLeaseRetryCountInDatabase(Long participationId, int expectedCount) {
        Integer retryCount = databaseJdbcTemplate().queryForObject(
                "SELECT lease_retry_count FROM battle_participations WHERE participation_id = ?",
                Integer.class,
                participationId
        );

        assertThat(retryCount).isEqualTo(expectedCount);
    }

    private JdbcTemplate databaseJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        return new JdbcTemplate(dataSource);
    }

    private Process startChildProcess(String mode, Path stateFile, Path logFile) throws Exception {
        String runtimeClasspath = System.getProperty(
                "recovery.test.runtime-classpath",
                System.getProperty("java.class.path")
        );
        assertThat(runtimeClasspath)
                .as("battleWorkerRecoveryIntegrationTest 작업이 전달한 테스트 런타임 classpath")
                .isNotBlank();

        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                runtimeClasspath,
                RestartProbe.class.getName()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(logFile.toFile());
        processBuilder.environment().put("RECOVERY_TEST_MODE", mode);
        processBuilder.environment().put("RECOVERY_TEST_STATE_FILE", stateFile.toString());
        processBuilder.environment().put("RECOVERY_TEST_JDBC_URL", MYSQL.getJdbcUrl());
        processBuilder.environment().put("RECOVERY_TEST_JDBC_USERNAME", MYSQL.getUsername());
        processBuilder.environment().put("RECOVERY_TEST_JDBC_PASSWORD", MYSQL.getPassword());
        processBuilder.environment().put("RECOVERY_TEST_JDBC_DRIVER", MYSQL.getDriverClassName());
        return processBuilder.start();
    }

    private boolean waitForFile(Path file, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            if (Files.exists(file)) {
                return true;
            }
            Thread.sleep(50);
        }

        return Files.exists(file);
    }

    private String childFailureMessage(String message, Process process, Path logFile) {
        try {
            String state = process != null && process.isAlive()
                    ? "alive"
                    : "exit=" + (process == null ? "not-started" : process.exitValue());
            String output = Files.exists(logFile) ? Files.readString(logFile) : "로그 없음";
            return message + " (" + state + ")\n" + output;
        } catch (Exception exception) {
            return message + " (자식 프로세스 로그 읽기 실패: " + exception.getMessage() + ")";
        }
    }

    private void destroyIfAlive(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private BattleParticipation waitForCompletedParticipation(
            BattleParticipationRepository repository,
            Long participationId,
            Duration timeout
    ) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            BattleParticipation participation = repository.findById(participationId).orElseThrow();

            if (participation.getParticipationStatus() == ParticipationStatus.GRADING_COMPLETED) {
                return participation;
            }

            Thread.sleep(50);
        }

        return repository.findById(participationId).orElseThrow();
    }

    private void printTestResult(String scenario, String... details) {
        log.info("""

                ==================== 서버 프로세스 재시작 통합 테스트 결과 ====================
                시나리오 : {}
                {}
                ============================================================================
                """, scenario, String.join("\n", details));
    }

    // 이 테스트 전용 설정이 다른 @DataJpaTest의 기본 애플리케이션 설정으로 탐색되지 않도록 격리
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
            User.class,
            DailyBattle.class,
            BattleCase.class,
            BattleParticipation.class,
            BattleSubmission.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            UserRepository.class,
            DailyBattleRepository.class,
            BattleCaseRepository.class,
            BattleParticipationRepository.class,
            BattleSubmissionRepository.class
    })
    @Import({
            BattleWorkerScheduler.class,
            BattleGradingWorker.class,
            BattleGradingService.class,
            BattleResultSaveService.class,
            BattleResultService.class,
            DailyBattleService.class,
            JpaConfig.class
    })
    static class RecoveryTestApplication {

        @Bean
        Clock clock(org.springframework.core.env.Environment environment) {
            return Clock.fixed(
                    Instant.parse(environment.getRequiredProperty("recovery.test.instant")),
                    KOREA_ZONE
            );
        }

        @Bean
        BattleSchedule battleSchedule() {
            return new BattleSchedule(OPEN_TIME, SUBMISSION_DEADLINE, RESULT_TIME);
        }

        @Bean(destroyMethod = "destroy")
        TaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("battle-process-restart-test-");
            scheduler.setRemoveOnCancelPolicy(true);
            return scheduler;
        }
    }

    public static class RestartProbe {

        public static void main(String[] args) throws Exception {
            String mode = requiredEnvironmentVariable("RECOVERY_TEST_MODE");
            Path stateFile = Path.of(requiredEnvironmentVariable("RECOVERY_TEST_STATE_FILE"));
            LocalTime applicationTime = mode.equals("prepare")
                    ? OPEN_TIME.minusMinutes(1)
                    : OPEN_TIME.plusMinutes(1);

            try (ConfigurableApplicationContext context = startChildApplication(applicationTime)) {
                if (mode.equals("prepare")) {
                    prepareExpiredGradingTask(context, stateFile);
                    new CountDownLatch(1).await();
                    return;
                }

                if (!mode.equals("recover")) {
                    throw new IllegalArgumentException("unknown recovery test mode: " + mode);
                }

                Long participationId = Long.valueOf(Files.readString(stateFile).trim());
                BattleParticipation recovered = waitForCompletedParticipationInChild(
                        context.getBean(BattleParticipationRepository.class),
                        participationId,
                        Duration.ofSeconds(10)
                );

                if (recovered.getParticipationStatus() != ParticipationStatus.GRADING_COMPLETED
                        || recovered.getLeaseRetryCount() != 1) {
                    throw new IllegalStateException(
                            "recovery failed: status=" + recovered.getParticipationStatus()
                                    + ", leaseRetryCount=" + recovered.getLeaseRetryCount()
                    );
                }
            }
        }

        private static ConfigurableApplicationContext startChildApplication(LocalTime time) {
            SpringApplication application = new SpringApplication(RecoveryTestApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);

            Map<String, Object> properties = commonApplicationProperties(time);
            properties.put("spring.datasource.url", requiredEnvironmentVariable("RECOVERY_TEST_JDBC_URL"));
            properties.put("spring.datasource.username", requiredEnvironmentVariable("RECOVERY_TEST_JDBC_USERNAME"));
            properties.put("spring.datasource.password", requiredEnvironmentVariable("RECOVERY_TEST_JDBC_PASSWORD"));
            properties.put("spring.datasource.driver-class-name", requiredEnvironmentVariable("RECOVERY_TEST_JDBC_DRIVER"));
            application.setDefaultProperties(properties);
            return application.run();
        }

        private static void prepareExpiredGradingTask(
                ConfigurableApplicationContext context,
                Path stateFile
        ) throws Exception {
            clearBattleData(context.getBean(DataSource.class));
            DailyBattleRepository dailyBattleRepository = context.getBean(DailyBattleRepository.class);
            BattleCaseRepository battleCaseRepository = context.getBean(BattleCaseRepository.class);
            BattleParticipationRepository participationRepository =
                    context.getBean(BattleParticipationRepository.class);
            BattleSubmissionRepository submissionRepository =
                    context.getBean(BattleSubmissionRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);

            DailyBattle battle = dailyBattleRepository.saveAndFlush(new DailyBattle(
                    TODAY,
                    Category.ARRAY,
                    "real process restart integration battle",
                    "forced JVM termination recovery integration test"
            ));
            BattleCase battleCase = battleCaseRepository.saveAndFlush(new BattleCase(
                    battle,
                    "1 2",
                    "3",
                    1
            ));
            User user = userRepository.saveAndFlush(new User("real-process-restart-user", null));
            BattleParticipation participation = new BattleParticipation(battle.getBattleId(), user);
            participation.markSubmit(context.getBean(Clock.class).instant());
            participation = participationRepository.saveAndFlush(participation);
            submissionRepository.saveAndFlush(new BattleSubmission(participation, battleCase, "3"));

            context.getBean(BattleGradingService.class)
                    .preemptSubmission(battle.getBattleId())
                    .orElseThrow();
            expireLease(context.getBean(DataSource.class), participation.getParticipationId());
            Files.writeString(stateFile, participation.getParticipationId().toString());
        }

        private static BattleParticipation waitForCompletedParticipationInChild(
                BattleParticipationRepository repository,
                Long participationId,
                Duration timeout
        ) throws InterruptedException {
            Instant deadline = Instant.now().plus(timeout);

            while (Instant.now().isBefore(deadline)) {
                BattleParticipation participation = repository.findById(participationId).orElseThrow();
                if (participation.getParticipationStatus() == ParticipationStatus.GRADING_COMPLETED) {
                    return participation;
                }
                Thread.sleep(50);
            }

            return repository.findById(participationId).orElseThrow();
        }

        private static String requiredEnvironmentVariable(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(name + " is required");
            }
            return value;
        }
    }
}
