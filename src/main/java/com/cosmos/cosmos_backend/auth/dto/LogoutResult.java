package com.cosmos.cosmos_backend.auth.dto;

import org.springframework.http.ResponseCookie;

public record LogoutResult(
        ResponseCookie accessToken,
        ResponseCookie refreshToken
) {

}
