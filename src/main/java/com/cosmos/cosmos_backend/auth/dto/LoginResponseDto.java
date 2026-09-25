package com.cosmos.cosmos_backend.auth.dto;

import lombok.AllArgsConstructor;

public record LoginResponseDto(
        Long userId,
        String userName,
        String userProfileImageUrl
) {
}