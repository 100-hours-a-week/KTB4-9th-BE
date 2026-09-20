package com.cosmos.cosmos_backend.auth.service;


import com.cosmos.cosmos_backend.auth.client.KakaoOAuthClient;
import com.cosmos.cosmos_backend.auth.domain.OAuthProvider;
import com.cosmos.cosmos_backend.auth.domain.RefreshTokenProvider;
import com.cosmos.cosmos_backend.auth.domain.entity.RefreshToken;
import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.domain.entity.UserOauthAccount;
import com.cosmos.cosmos_backend.auth.dto.*;
import com.cosmos.cosmos_backend.auth.jwt.JwtTokenProvider;
import com.cosmos.cosmos_backend.auth.repository.RefreshTokenRepository;
import com.cosmos.cosmos_backend.auth.repository.UserOauthAccountRepository;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private KakaoOAuthClient kakaoOAuthClient;

    @Autowired
    private UserOauthAccountRepository userOauthAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenProvider refreshTokenProvider;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public LoginResult login(String provider, String code) {

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
        // 7. 컨트롤러로 결과 반환
        User user;

        if(userOauthAccount.isPresent()){
            user =  userOauthAccount.get().getUser();

        } else {
            User newUser = new User(kakaoUser.kakaoAccount().profile().nickname(), kakaoUser.kakaoAccount().profile().profileImageUrl());
            userRepository.save(newUser);

            UserOauthAccount newUserOauthAccount = new UserOauthAccount(newUser, oauthProvider,kakaoUser.oauthUserId().toString());
            userOauthAccountRepository.save(newUserOauthAccount);

            user =newUser;
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getUsername());

        String originRefreshToken = refreshTokenProvider.createRefreshToken();

        String refreshTokenHash =
                refreshTokenProvider.hashRefreshToken(originRefreshToken);

        RefreshToken refreshToken = new RefreshToken(user, refreshTokenHash,refreshTokenProvider.getExpiresAt());

        refreshTokenRepository.save(refreshToken);

        LoginResponseDto responseDto = new LoginResponseDto(user.getId(),
                user.getUsername(),
                user.getProfileImageUrl());

        return new LoginResult(responseDto,accessToken,originRefreshToken);
    }

    @Transactional
    public TokenRefreshResult refreshToken(String originRefreshToken) {

        // 1. 받은 Refresh Token 원문을 hash
        String refreshTokenHash =
                refreshTokenProvider.hashRefreshToken(originRefreshToken);

        // 2. DB에서 Refresh Token 조회
        RefreshToken savedRefreshToken =
                refreshTokenRepository
                        .findByRefreshTokenHash(refreshTokenHash)
                        .orElseThrow(() ->
                                new RuntimeException("refresh_token_invalid"));

        // 3. 만료 여부 확인
        if (savedRefreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(savedRefreshToken);
            throw new RuntimeException("refresh_token_invalid");
        }

        // 4. 해당 Refresh Token의 사용자 조회
        User user = savedRefreshToken.getUser();

        // 5. 새로운 Access Token 생성
        String newAccessToken =
                jwtTokenProvider.createAccessToken(
                        user.getId(),
                        user.getUsername()
                );

        // 6. 새로운 Refresh Token 생성
        String newOriginRefreshToken =
                refreshTokenProvider.createRefreshToken();

        // 7. 새로운 Refresh Token hash
        String newRefreshTokenHash =
                refreshTokenProvider.hashRefreshToken(newOriginRefreshToken);

        // 8. 기존 Refresh Token 제거
        refreshTokenRepository.delete(savedRefreshToken);

        // 9. 새로운 Refresh Token 저장
        RefreshToken newRefreshToken = new RefreshToken(
                user,
                newRefreshTokenHash,
                refreshTokenProvider.getExpiresAt()
        );

        refreshTokenRepository.save(newRefreshToken);

        // 10. Controller에 새 토큰 전달
        return new TokenRefreshResult(
                newAccessToken,
                newOriginRefreshToken
        );
    }

    @Transactional
    public void logout(String originRefreshToken) {

        // 1. 전달받은 Refresh Token 원문을 hash로 변환
        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(originRefreshToken);


        // 2. 해당 hash를 가진 RefreshToken을 DB에서 조회
        Optional<RefreshToken> savedRefreshToken = refreshTokenRepository.findByRefreshTokenHash(refreshTokenHash);


        // 3. 존재한다면 해당 RefreshToken 행 삭제
        if (savedRefreshToken.isPresent()) {
            refreshTokenRepository.delete(savedRefreshToken.get());
        }
    }

}