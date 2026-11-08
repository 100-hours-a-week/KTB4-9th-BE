package com.cosmos.cosmos_backend.auth.dto;

import org.springframework.http.ResponseCookie;

public record WithdrawalResult(
        WithdrawalResponseDto response,
        ResponseCookie accessToken,
        ResponseCookie refreshToken
) {
}