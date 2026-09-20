package com.cosmos.cosmos_backend.approach.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApproachSubmitRequest(
        @NotBlank(message = "selected_category_is_required") String selectedCategory,
        @NotBlank(message = "approach_is_required") String approach
) {
}
