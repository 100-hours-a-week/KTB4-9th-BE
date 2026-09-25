package com.cosmos.cosmos_backend.problem.dto.request;

import jakarta.validation.constraints.NotBlank;

public record HintRequest(
        @NotBlank(message = "language_is_required") String language
) {
}
