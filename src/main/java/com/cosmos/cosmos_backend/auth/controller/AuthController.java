package com.cosmos.cosmos_backend.auth.controller;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.dto.LoginResponseDto;
import com.cosmos.cosmos_backend.auth.dto.LoginResult;
import com.cosmos.cosmos_backend.auth.dto.LogoutResult;
import com.cosmos.cosmos_backend.auth.dto.TokenRefreshResult;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.auth.service.AuthService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "인증", description = "카카오 OAuth 로그인과 사용자 인증 API")
public class AuthController {

    private final AuthService authService;

    private final UserRepository userRepository;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    // oauth 로그인 결과에 대한 콜백
    @GetMapping("/oauth/{provider}/callback")
    @Operation(summary = "OAuth 로그인 콜백", description = "OAuth 인가 코드로 로그인하고 accessToken과 refreshToken 쿠키를 발급합니다.", security = {})
    public ResponseEntity<Void> login(
            @Parameter(description = "OAuth 제공자. 현재 KAKAO만 지원합니다.", required = true, example = "kakao") @PathVariable String provider,
            @Parameter(description = "OAuth 제공자가 발급한 인가 코드", required = true) @RequestParam String code
    ) {

        // 1. 요청에서 provider와 code를 받음
        // 2. 실제 로그인 처리는 Service에게 맡김
        LoginResult loginResult = authService.login(provider, code);

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, loginResult.accessToken().toString())
                .header(HttpHeaders.SET_COOKIE, loginResult.refreshToken().toString())
                .location(URI.create("https://cosmoscode.site/"))
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "accessToken 쿠키로 인증된 사용자의 기본 정보를 조회합니다.")
    public LoginResponseDto getMe(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

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
    @Operation(summary = "토큰 재발급", description = "refreshToken 쿠키를 검증하고 accessToken과 refreshToken 쿠키를 새로 발급합니다.", security = {})
    public ResponseEntity<Void> refreshToken(
            @Parameter(description = "로그인 시 발급된 refreshToken 쿠키", required = true)
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String refreshToken
    ) {

        // 1. Refresh Token 쿠키가 없는 경우
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "refresh_token_missing");
        }

        // 2. Refresh Token 검증 + 새로운 토큰 발급
        TokenRefreshResult result =
                authService.refreshToken(refreshToken);

        // 5. Body 없이 새로운 쿠키 두 개 전달
        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        result.accessToken().toString()
                )
                .header(
                        HttpHeaders.SET_COOKIE,
                        result.refreshToken().toString()
                )
                .build();
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "저장된 refreshToken을 폐기하고 인증 쿠키를 삭제합니다.", security = {})
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(description = "폐기할 refreshToken 쿠키. 쿠키가 없어도 로그아웃 응답은 정상 처리됩니다.")
            @CookieValue(name = "refreshToken", required = false)
            String refreshToken
    ) {

        // 1. refreshToken이 존재하면 AuthService.logout() 호출
        if (refreshToken != null && !refreshToken.isBlank()){
            LogoutResult logoutResult = authService.logout(refreshToken);

            // 두 Set-Cookie를 담아서 204 반환
            return ResponseEntity
                    .noContent()
                    .header(
                            HttpHeaders.SET_COOKIE,
                            logoutResult.accessToken().toString()
                    )
                    .header(
                            HttpHeaders.SET_COOKIE,
                            logoutResult.refreshToken().toString()
                    )
                    .build();

        } else {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "refresh_token_missing");
        }

    }

}
