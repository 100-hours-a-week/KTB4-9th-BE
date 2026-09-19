package com.cosmos.cosmos_backend.auth.client;

import com.cosmos.cosmos_backend.auth.dto.KakaoTokenResponseDto;
import com.cosmos.cosmos_backend.auth.dto.KakaoUserInfoResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoOAuthClient {

    // 필요한 설정값들
    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    // 토큰 요청
    public KakaoTokenResponseDto getToken(String code) {

        // 요청 형식 : form-urlencoded 방식 구성
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);
        formData.add("client_secret", clientSecret);

        // 카카오 Token API에 HTTP 요청
        RestClient restClient = RestClient.create("https://kauth.kakao.com");

        KakaoTokenResponseDto kakaoTokenResponseDto = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(KakaoTokenResponseDto.class);

        return kakaoTokenResponseDto;
    }

    // 사용자 정보 조회
    public KakaoUserInfoResponseDto getUserInfo(String accessToken) {

        RestClient restClient = RestClient.create("https://kapi.kakao.com");

        KakaoUserInfoResponseDto kakaoUserInfoResponseDto = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/user/me")
                        .queryParam("property_keys", "[\"kakao_account.profile\"]")
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve()
                .body(KakaoUserInfoResponseDto.class);

        return kakaoUserInfoResponseDto;
    }
}