package com.cosmos.cosmos_backend.approach.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ApproachSubmitRequest(
        @NotBlank(message = "selected_category_is_required") String selectedCategory,
        @JsonProperty("natural_solution") @NotBlank(message = "approach_is_required") String naturalSolution
) {
}
