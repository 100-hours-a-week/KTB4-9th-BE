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
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import com.cosmos.cosmos_backend.ranking.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;

    private final UserOauthAccountRepository userOauthAccountRepository;

    private final UserRepository userRepository;

    private final JwtTokenProvider jwtTokenProvider;

    private final RefreshTokenProvider refreshTokenProvider;

    private final RefreshTokenRepository refreshTokenRepository;

    private final UserPointRepository userPointRepository;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

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

            user = newUser;

            UserPoint userPoint = new UserPoint(
                    user,
                    0L,
                    0L,
                    0L
            );

            userPointRepository.save(userPoint);
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

        // Access Token 쿠키 생성
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTokenExpiration)
                .build();

        // refresh token 쿠키 생성
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshToken", originRefreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenExpiration)
                .build();

        return new LoginResult(responseDto,accessTokenCookie,refreshTokenCookie);
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
                                new BusinessException(HttpStatus.UNAUTHORIZED,"refresh_token_invalid"));

        // 3. 만료 여부 확인 (만료 시각이 UTC 기준이라 비교도 UTC로)
        if (savedRefreshToken.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            refreshTokenRepository.delete(savedRefreshToken);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "refresh_token_invalid");
        }

        // 4. 해당 Refresh Token의 사용자 조회
        User user = savedRefreshToken.getUser();

        // 5. 새로운 Access Token 생성
        String accessToken =
                jwtTokenProvider.createAccessToken(
                        user.getId(),
                        user.getUsername()
                );

        // 6. 새로운 Refresh Token 생성
        String changedOriginRefreshToken =
                refreshTokenProvider.createRefreshToken();

        // 7. 새로운 Refresh Token hash
        String newRefreshTokenHash =
                refreshTokenProvider.hashRefreshToken(changedOriginRefreshToken);

        // 8. 기존 Refresh Token 제거
        refreshTokenRepository.delete(savedRefreshToken);

        // 9. 새로운 Refresh Token 저장
        RefreshToken newRefreshToken = new RefreshToken(
                user,
                newRefreshTokenHash,
                refreshTokenProvider.getExpiresAt()
        );

        refreshTokenRepository.save(newRefreshToken);

        // 3. 새로운 Access Token 쿠키
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTokenExpiration)
                .build();

        // 4. 새로운 Refresh Token 쿠키
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshToken", changedOriginRefreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenExpiration)
                .build();

        // 10. Controller에 새 토큰 전달
        return new TokenRefreshResult(
                accessTokenCookie,
                refreshTokenCookie
        );
    }

    @Transactional
    public LogoutResult logout(String originRefreshToken) {

        // 1. 전달받은 Refresh Token 원문을 hash로 변환
        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(originRefreshToken);


        // 2. 해당 hash를 가진 RefreshToken을 DB에서 조회
        Optional<RefreshToken> savedRefreshToken = refreshTokenRepository.findByRefreshTokenHash(refreshTokenHash);

        // 3. 존재한다면 해당 RefreshToken 행 삭제
        if (savedRefreshToken.isPresent()) {
            refreshTokenRepository.delete(savedRefreshToken.get());
        }

        // 4. accessToken 삭제용 쿠키 생성
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        // 5. refreshToken 삭제용 쿠키 생성
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return new LogoutResult(accessTokenCookie, refreshTokenCookie);
    }

}