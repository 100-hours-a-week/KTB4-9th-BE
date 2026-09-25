package com.cosmos.cosmos_backend.auth.controller;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.dto.LoginResponseDto;
import com.cosmos.cosmos_backend.auth.dto.LoginResult;
import com.cosmos.cosmos_backend.auth.dto.TokenRefreshResult;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final UserRepository userRepository;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // oauth 로그인 결과에 대한 콜백
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> login(
            @PathVariable String provider,
            @RequestParam String code
    ) {

        // 1. 요청에서 provider와 code를 받음
        // 2. 실제 로그인 처리는 Service에게 맡김
        LoginResult loginResult = authService.login(provider, code);

        // Access Token 쿠키 생성
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", loginResult.accessToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTokenExpiration)
                .build();

        // refresh token 쿠키 생성
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshToken", loginResult.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenExpiration)
                .build();


        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .location(URI.create("https://cosmoscode.site/"))
                .build();
    }

    @GetMapping("/me")
    public LoginResponseDto getMe(@AuthenticationPrincipal Jwt jwt) {

        // 1. Spring Security가 검증한 JWT에서 userId(sub) 가져오기
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. DB에서 현재 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 3. 프론트에 사용자 정보 반환
        return new LoginResponseDto(
                user.getId(),
                user.getUsername(),
                user.getProfileImageUrl()
        );
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<Void> refreshToken(
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String refreshToken
    ) {

        // 1. Refresh Token 쿠키가 없는 경우
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("refresh_token_missing");
        }

        // 2. Refresh Token 검증 + 새로운 토큰 발급
        TokenRefreshResult result =
                authService.refreshToken(refreshToken);

        // 3. 새로운 Access Token 쿠키
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", result.accessToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTokenExpiration)
                .build();

        // 4. 새로운 Refresh Token 쿠키
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenExpiration)
                .build();

        // 5. Body 없이 새로운 쿠키 두 개 전달
        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        accessTokenCookie.toString()
                )
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false)
            String refreshToken
    ) {

        // 1. refreshToken이 존재하면 AuthService.logout() 호출
        if (refreshToken != null && !refreshToken.isBlank()){
            authService.logout(refreshToken);
        }

        // 2. accessToken 삭제용 쿠키 생성
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        // 3. refreshToken 삭제용 쿠키 생성
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();

        // 4. 두 Set-Cookie를 담아서 204 반환
        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        accessTokenCookie.toString()
                )
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .build();
    }

}
