package com.cosmos.cosmos_backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String ACCESS_TOKEN_COOKIE = "accessToken";

    @Bean
    public OpenAPI cosmosOpenApi() {
        SecurityScheme cookieAuthentication = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name(ACCESS_TOKEN_COOKIE)
                .description("로그인 시 발급되는 JWT accessToken 쿠키");

        return new OpenAPI()
                .info(new Info()
                        .title("COSMOS API")
                        .description("COSMOS 백엔드 REST API 명세")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(ACCESS_TOKEN_COOKIE, cookieAuthentication))
                .addSecurityItem(new SecurityRequirement().addList(ACCESS_TOKEN_COOKIE));
    }
}
