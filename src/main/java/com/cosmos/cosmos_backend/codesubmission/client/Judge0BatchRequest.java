package com.cosmos.cosmos_backend.codesubmission.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Judge0 배치 제출 요청. 코드와 입출력은 base64로 인코딩된 값. */
public record Judge0BatchRequest(List<Submission> submissions) {

    /** 테스트케이스 한 건의 제출 내용 (제한 값이 없으면 null이라 JSON에서 빠짐). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Submission(
            @JsonProperty("language_id") Integer languageId,
            @JsonProperty("source_code") String sourceCode,
            String stdin,
            @JsonProperty("expected_output") String expectedOutput,
            @JsonProperty("cpu_time_limit") Double cpuTimeLimit,
            @JsonProperty("memory_limit") Integer memoryLimit
    ) {
    }
}
