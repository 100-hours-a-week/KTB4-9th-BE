package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.dailyBattle.domain.BattleSubmissionEventStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.OutboxEvent;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.OutboxEventPreemptResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.repository.OutboxEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxStatusChangeService {

    // TODO : 몇 초 기준으로 할지 부하테스트 해보고 바꾸기
    private static final long PUBLISHER_LEASE_SECONDS = 5;

    private static final int PUBLISH_BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;

    private final Clock clock;

    //작업 선점 메소드 (선점 + 상태 변경 한 트랜잭션)
    @Transactional
    public OutboxEventPreemptResponseDto preemptOutboxEvent() {

        // 임대가 만료된 PUBLISHING 이벤트 먼저 조회
        List<OutboxEvent> expiredPublishingEvents =
                outboxEventRepository.findExpiredPublishingForUpdate(PUBLISH_BATCH_SIZE);

        // 최대 배치 크기에서 남은 개수 계산
        int pendingLimit = PUBLISH_BATCH_SIZE - expiredPublishingEvents.size();

        // 남은 개수만큼 PENDING 이벤트 조회
        List<OutboxEvent> pendingEvents = pendingLimit > 0
                ? outboxEventRepository.findPendingForUpdate(pendingLimit)
                : List.of();

        // 실제 선점할 이벤트를 하나의 목록으로 합치기
        List<OutboxEvent> outboxEvents = new ArrayList<>(PUBLISH_BATCH_SIZE);
        outboxEvents.addAll(expiredPublishingEvents);
        outboxEvents.addAll(pendingEvents);

        // 선점할 작업 담을 리스트
        List<OutboxEvent> publishEvents = new ArrayList<>();

        // publishing 상태 변경
        Instant leaseExpiresAt = clock.instant().plusSeconds(PUBLISHER_LEASE_SECONDS);

        UUID leaseToken = UUID.randomUUID();

        for (OutboxEvent outboxEvent : outboxEvents) {
            // 재선점 횟수 3회 초과시 발행 실패 처리
            if (outboxEvent.getPublishedAttemptCount() >= 3){
                outboxEvent.changeStatusFailed();
                continue;
            }

            // 실제 발행할 이벤트 선점
            outboxEvent.changedPublishingStatus(leaseToken, leaseExpiresAt);
            outboxEvent.increasePublishedAttemptCount();

            publishEvents.add(outboxEvent);

        }

        OutboxEventPreemptResponseDto outboxEventPreemptResponse = new OutboxEventPreemptResponseDto(
                publishEvents,
                leaseToken
        );

        return outboxEventPreemptResponse;

    }


    // 메세지 발행 상태로 변경하는 메소드
    @Transactional
    public void changeOutboxSent(Long outboxEventId, UUID leaseToken) {

        LocalDateTime now = LocalDateTime.now(clock);

        outboxEventRepository.markAsSent(
                outboxEventId,
                leaseToken,
                BattleSubmissionEventStatus.SENT,
                BattleSubmissionEventStatus.PUBLISHING,
                now
        );

    }
}
