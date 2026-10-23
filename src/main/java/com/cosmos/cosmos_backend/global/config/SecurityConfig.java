package com.cosmos.cosmos_backend.global.config;

import com.cosmos.cosmos_backend.auth.jwt.CookieBearerTokenResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieBearerTokenResolver cookieBearerTokenResolver
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                // CORS 설정
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/oauth/**", "/auth/token/refresh",
                                "/auth/logout"
                        ).permitAll()
                        // dev 프로필에서만 생성되는 부하 테스트 fixture 엔드포인트
                        // 컨트롤러에서 별도의 X-Load-Test-Secret을 검증
                        .requestMatchers(HttpMethod.POST, "/internal/load-test/battle/users").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/daily-problems").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.GET,"/problems/new"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/problems"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST,"/daily-battles/problem")).permitAll()
                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(cookieBearerTokenResolver)
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${frontend.base-url}") String frontendBaseUrl
    ) {

        CorsConfiguration configuration = new CorsConfiguration();

        // 프론트 서버
        configuration.setAllowedOrigins(List.of(frontendBaseUrl));

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
