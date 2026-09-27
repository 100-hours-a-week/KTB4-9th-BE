package com.cosmos.cosmos_backend.codesubmission.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Judge0 배치 결과 조회 응답. */
public record Judge0BatchResult(List<Item> submissions) {

    /** 제출 한 건의 상태 (1: 대기, 2: 처리 중, 3 이상: 채점 끝). */
    public record Item(String token, @JsonProperty("status_id") Integer statusId) {
    }
}
