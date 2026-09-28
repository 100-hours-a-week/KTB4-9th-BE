package com.cosmos.cosmos_backend.problem.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record HintRequest(
        @Schema(description = "힌트를 조회할 프로그래밍 언어", allowableValues = {"PYTHON", "JAVA", "JAVASCRIPT", "CPP"}, requiredMode = Schema.RequiredMode.REQUIRED, example = "JAVA")
        @NotBlank(message = "language_is_required") String language
) {
}
