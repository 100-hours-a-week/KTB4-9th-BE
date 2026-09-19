package com.cosmos.cosmos_backend.auth.controller;

import com.cosmos.cosmos_backend.auth.domain.OAuthProvider;
import com.cosmos.cosmos_backend.auth.dto.KakaoUserInfoResponseDto;
import com.cosmos.cosmos_backend.auth.dto.LoginResponseDto;
import com.cosmos.cosmos_backend.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    // oauth 로그인 결과에 대한 콜백
    @GetMapping("/oauth/{provider}/callback")
    public LoginResponseDto login(
            @PathVariable String provider,
            @RequestParam String code
    ) {

        // 1. 요청에서 provider와 code를 받음
        // 2. 실제 로그인 처리는 Service에게 맡김
        LoginResponseDto loginResult = authService.login(provider, code);

        // 3. Service가 처리한 결과를 HTTP 응답으로 반환
        return loginResult;
    }


}
