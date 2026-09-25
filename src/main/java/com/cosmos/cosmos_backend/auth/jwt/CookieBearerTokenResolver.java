package com.cosmos.cosmos_backend.auth.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    @Override
    public String resolve(HttpServletRequest request) {

        if ("/auth/token/refresh".equals(request.getRequestURI())|| "/auth/logout".equals(request.getRequestURI())) {
            return null;
        }

        // 1. 요청에 포함된 모든 쿠키 가져오기
        Cookie[] cookies = request.getCookies();

        // 2. 쿠키가 아예 없는 경우
        if (cookies == null) {
            return null;
        }

        // 3. accessToken 쿠키 찾기
        for (Cookie cookie : cookies) {
            if ("accessToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        // 4. 쿠키는 있지만 accessToken 쿠키가 없는 경우
        return null;
    }
}