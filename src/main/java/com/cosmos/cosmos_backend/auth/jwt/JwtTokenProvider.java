package com.cosmos.cosmos_backend.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JwtTokenProvider {

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    private final JwtEncoder jwtEncoder;

    public JwtTokenProvider(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String createAccessToken(Long userId, String username) {

        // 1. 현재 시간 구하기
        Instant now = Instant.now();

        // 2. 만료 시간 구하기 (현재 + 30분)
        Instant expiresAt = now.plusSeconds(accessTokenExpiration);

        // 3. JWT Payload 만들기
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        // 4. JwtEncoder로 JWT 생성
        JwtEncoderParameters parameters =
                JwtEncoderParameters.from(claims);

        // 실제 JWT 생성
        Jwt jwt = jwtEncoder.encode(parameters);

        // 5. 최종 JWT 문자열 반환
        return jwt.getTokenValue();
    }
}