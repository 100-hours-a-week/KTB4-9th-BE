package com.cosmos.cosmos_backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Bean
    public SecretKey jwtSecretKey() {

        // 1. Base64 문자열을 원래 byte[]로 디코딩
        byte[] keyBytes = Base64.getDecoder().decode(secret);

        // 2. byte[]를 HMAC-SHA256용 SecretKey로 만들기
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        // 3. Spring Bean으로 등록될 객체 반환
        return secretKey;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secretKey) {
        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .build();
    }

}