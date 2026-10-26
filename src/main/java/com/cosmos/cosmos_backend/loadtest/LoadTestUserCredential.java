package com.cosmos.cosmos_backend.loadtest;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoadTestUserCredential(
        @JsonProperty("user_id")
        Long userId,

        @JsonProperty("access_token")
        String accessToken
) {
}
