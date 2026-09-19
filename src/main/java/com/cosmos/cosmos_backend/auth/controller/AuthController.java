package com.cosmos.cosmos_backend.auth.controller;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.dto.LoginResponseDto;
import com.cosmos.cosmos_backend.auth.dto.LoginResult;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.auth.service.AuthService;
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
public class AuthController {

    @Autowired
    private AuthService authService;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Autowired
    private UserRepository userRepository;

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

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .location(URI.create("http://localhost:5173/"))
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

}
