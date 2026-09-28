package com.cosmos.cosmos_backend.codesubmission.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CodeSubmitRequest(
        @Schema(description = "채점할 프로그래밍 언어", allowableValues = {"PYTHON", "JAVA", "JAVASCRIPT", "CPP"}, requiredMode = Schema.RequiredMode.REQUIRED, example = "JAVA")
        @NotBlank(message = "language_is_required") String language,

        @Schema(description = "채점할 소스 코드", requiredMode = Schema.RequiredMode.REQUIRED, example = "public class Main { public static void main(String[] args) { } }")
        @JsonProperty("source_code") @NotBlank(message = "source_code_is_required") String sourceCode
) {
}
