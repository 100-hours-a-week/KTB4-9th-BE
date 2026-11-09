package com.cosmos.cosmos_backend.approach.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** DB의 UTC LocalDateTime을 응답용 UTC 시각(…Z)으로 바꾸는 도우미. */
public final class UtcInstants {

    private UtcInstants() {
    }

    // 값이 없으면 null 그대로 반환
    public static Instant of(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
