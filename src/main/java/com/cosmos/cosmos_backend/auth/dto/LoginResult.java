package com.cosmos.cosmos_backend.auth.dto;

import org.springframework.http.ResponseCookie;

public record LoginResult(
        LoginResponseDto response,
        ResponseCookie accessToken,
        ResponseCookie refreshToken
) {
}