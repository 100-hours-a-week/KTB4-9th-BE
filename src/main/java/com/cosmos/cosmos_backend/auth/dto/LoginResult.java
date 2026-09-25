package com.cosmos.cosmos_backend.auth.dto;

public record LoginResult(
        LoginResponseDto response,
        String accessToken,
        String refreshToken
) {
}