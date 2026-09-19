package com.cosmos.cosmos_backend.auth.dto;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class LoginResponseDto {

    Long userId;
    String userName;
    String userProfileImageUrl;
}
