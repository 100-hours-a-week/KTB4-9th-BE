package com.cosmos.cosmos_backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 카카오 사용자 정보 응답 dto
public record KakaoUserInfoResponseDto(

        @JsonProperty("id")
        Long oauthUserId,

        @JsonProperty("kakao_account")
        KakaoAccount kakaoAccount
) {
    public record KakaoAccount(
            Profile profile
    ){}

    public record Profile(
            String nickname,

            @JsonProperty("profile_image_url")
            String profileImageUrl
    ) {}
}

