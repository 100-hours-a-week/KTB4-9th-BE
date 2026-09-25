package com.cosmos.cosmos_backend.auth.dto;

public record TokenRefreshResult(
        String accessToken,
        String refreshToken
) {
}
