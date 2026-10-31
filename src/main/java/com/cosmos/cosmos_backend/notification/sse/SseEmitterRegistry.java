package com.cosmos.cosmos_backend.notification.sse;

import com.cosmos.cosmos_backend.notification.event.EvaluationCompletedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 이 서버에 연결된 사용자별 SSE 연결을 관리하고 평가 완료 이벤트를 전송. */
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long HEARTBEAT_SECONDS = 15;

    // 사용자별 연결 목록 (탭을 여러 개 열 수 있어서 리스트)
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // SSE 전송 전용 스레드
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "sse-sender");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    void startHeartbeat() {
        executor.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }

    /** 새 연결을 등록하고 connected 이벤트를 보냄. */
    public SseEmitter register(Long userId) {
        // 1. 연결 객체를 만들고 사용자 목록에 추가
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.compute(userId, (id, list) -> {
            List<SseEmitter> target = list == null ? new CopyOnWriteArrayList<>() : list;
            target.add(emitter);
            return target;
        });

        // 2. 연결이 끝나거나 실패하면 목록에서 제거
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> remove(userId, emitter));

        // 3. 연결 성공을 알리는 첫 이벤트 전송
        send(userId, emitter, SseEmitter.event().name("connected")
                .data(Map.of("message", "sse_connected"), MediaType.APPLICATION_JSON));
        return emitter;
    }

    /** 이 서버에 연결된 해당 사용자에게 이벤트를 전송. 전송은 별도 스레드에서 실행. */
    public void dispatch(EvaluationCompletedEvent event) {
        executor.execute(() -> deliver(event));
    }

    private void deliver(EvaluationCompletedEvent event) {
        // 1. 이 서버에 그 사용자의 연결이 없으면 종료
        List<SseEmitter> targets = emitters.get(event.userId());
        if (targets == null) {
            return;
        }

        // 2. 열려 있는 모든 탭에 전송
        for (SseEmitter emitter : targets) {
            send(event.userId(), emitter, SseEmitter.event()
                    .id(event.eventId())
                    .name("evaluation-completed")
                    .data(EvaluationCompletedPayload.from(event), MediaType.APPLICATION_JSON));
        }
    }

    // 모든 연결에 heartbeat 주석을 보냄
    private void sendHeartbeat() {
        emitters.forEach((userId, list) ->
                list.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("heartbeat"))));
    }

    // 전송하고, 실패하면 그 연결을 정리
    private void send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

    // 목록에서 연결을 제거하고, 비면 사용자 항목도 지움
    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
