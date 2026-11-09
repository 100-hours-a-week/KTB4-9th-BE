package com.cosmos.cosmos_backend.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import com.cosmos.cosmos_backend.notification.repository.NotificationRepository;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 테스트 자체를 트랜잭션으로 감싸지 않아야 커밋 시점을 확인할 수 있다.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({NotificationService.class, EvaluationCompletedListener.class,
        EvaluationCompletedEventCommitTest.TestBeans.class})
class EvaluationCompletedEventCommitTest {

    @TestConfiguration
    static class TestBeans {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-19T03:30:06Z"), ZoneId.of("Asia/Seoul"));
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @MockitoBean
    private EvaluationEventBus eventBus;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void event_isPublishedToBus_afterCommit() {
        // When
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notificationService.createEvaluationNotification(
                        42L, 101L, 9001L, NotificationType.NL_EVALUATION_DONE, "두 수의 합"));

        // Then
        ArgumentCaptor<EvaluationCompletedEvent> captor = ArgumentCaptor.forClass(EvaluationCompletedEvent.class);
        verify(eventBus).publish(captor.capture());
        Long savedId = notificationRepository.findByEvaluationTaskId(9001L).orElseThrow().getId();
        assertThat(captor.getValue().notificationId()).isEqualTo(savedId);
        assertThat(captor.getValue().status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void event_isNotPublished_whenTransactionRollsBack() {
        // When
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notificationService.createEvaluationNotification(
                    42L, 101L, 9002L, NotificationType.NL_EVALUATION_DONE, "두 수의 합");
            status.setRollbackOnly();
        });

        // Then
        verifyNoInteractions(eventBus);
        assertThat(notificationRepository.findByEvaluationTaskId(9002L)).isEmpty();
    }
}
