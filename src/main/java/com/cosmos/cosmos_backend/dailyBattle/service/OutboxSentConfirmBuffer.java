package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.dailyBattle.domain.OutboxSentConfirm;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxSentConfirmBuffer {

    private final Set<OutboxSentConfirm> confirmedEvents = ConcurrentHashMap.newKeySet();

    // RabbitMQ Confirm 성공 결과를 메모리 Buffer에 저장
    public void add(Long outboxEventId, UUID leaseToken) {
        confirmedEvents.add(new OutboxSentConfirm(outboxEventId, leaseToken));
    }

    // Buffer에서 최대 배치 크기만큼 Confirm 결과 꺼내기
    public List<OutboxSentConfirm> drain(int batchSize) {
        List<OutboxSentConfirm> drainedEvents = new ArrayList<>(batchSize);

        for (OutboxSentConfirm confirmedEvent : confirmedEvents) {
            if (drainedEvents.size() >= batchSize) {
                break;
            }

            if (confirmedEvents.remove(confirmedEvent)) {
                drainedEvents.add(confirmedEvent);
            }
        }

        return drainedEvents;
    }

    // DB 반영 실패한 Confirm 결과를 다음 배치에서 재처리
    public void addAll(List<OutboxSentConfirm> confirmedEvents) {
        this.confirmedEvents.addAll(confirmedEvents);
    }
}
