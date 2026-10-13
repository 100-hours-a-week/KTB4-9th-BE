package com.cosmos.cosmos_backend.global.config;

import com.cosmos.cosmos_backend.global.RequestTimeFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RequestTimeFilterConfig {

    @Bean
    public FilterRegistrationBean<RequestTimeFilter> requestTimeFilter() {

        FilterRegistrationBean<RequestTimeFilter> registration = new FilterRegistrationBean<>();

        // 1. 필터 등록
        registration.setFilter(new RequestTimeFilter());

        // 2. 실행 순서
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        // 3. 배틀 제출에만 적용
        registration.addUrlPatterns("/daily-battles/*");

        return registration;
    }
}