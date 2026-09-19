package com.cosmos.cosmos_backend.auth.service;


import com.cosmos.cosmos_backend.auth.client.KakaoOAuthClient;
import com.cosmos.cosmos_backend.auth.domain.OAuthProvider;
import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.domain.entity.UserOauthAccount;
import com.cosmos.cosmos_backend.auth.dto.KakaoTokenResponseDto;
import com.cosmos.cosmos_backend.auth.dto.KakaoUserInfoResponseDto;
import com.cosmos.cosmos_backend.auth.dto.LoginResponseDto;
import com.cosmos.cosmos_backend.auth.repository.UserOauthAccountRepository;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private KakaoOAuthClient kakaoOAuthClient;

    @Autowired
    private UserOauthAccountRepository userOauthAccountRepository;

    @Autowired
    private UserRepository userRepository;

    public LoginResponseDto login(String provider, String code) {

        OAuthProvider oauthProvider = OAuthProvider.valueOf(provider.toUpperCase());

        // 1. 인가 코드 → 카카오 Access Token 요청
        KakaoTokenResponseDto kakaoToken =
                kakaoOAuthClient.getToken(code);

        // 2. 카카오 Access Token → 사용자 정보 요청
        KakaoUserInfoResponseDto kakaoUser =
                kakaoOAuthClient.getUserInfo(kakaoToken.accessToken());

        // 3. 우리 DB에서 해당 OAuth 사용자가 있는지 조회
        // 우리 DB에서 해당 provider, provider_user_id를 가진 사람이 있는지
        Optional<UserOauthAccount> userOauthAccount = userOauthAccountRepository.findByOauthProviderAndProviderUserId(oauthProvider,kakaoUser.oauthUserId().toString());

        // 4. 없다면 회원 등록, provider account 등록
        // 5. 있다면 기존 회원 사용, user 정보 반환
        // 6. COSMOS Access/Refresh Token 생성
        if(userOauthAccount.isPresent()){

            User user =  userOauthAccount.get().getUser();
            LoginResponseDto loginResponseDto = new LoginResponseDto(user.getId(),user.getUsername(),user.getProfileImageUrl());
            return loginResponseDto;

        } else {
            User newUser = new User(kakaoUser.kakaoAccount().profile().nickname(), kakaoUser.kakaoAccount().profile().profileImageUrl());
            userRepository.save(newUser);

            UserOauthAccount newUserOauthAccount = new UserOauthAccount(newUser, oauthProvider,kakaoUser.oauthUserId().toString());
            userOauthAccountRepository.save(newUserOauthAccount);

            LoginResponseDto loginResponseDto = new LoginResponseDto(newUser.getId(),newUser.getUsername(),newUser.getProfileImageUrl());
            return loginResponseDto;
        }

        // 7. Controller로 결과 반환

    }
}