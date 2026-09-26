package com.cosmos.cosmos_backend.codesubmission.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CodeSubmitRequest(
        @NotBlank(message = "language_is_required") String language,
        @JsonProperty("source_code") @NotBlank(message = "source_code_is_required") String sourceCode
) {
}
