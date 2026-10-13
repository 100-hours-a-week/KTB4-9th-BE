package com.cosmos.cosmos_backend.global;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

public class RequestTimeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Filter 실행 시각 기록
        Instant filterTime = Instant.now();

        // 2. request attribute에 저장
        request.setAttribute("battle.filterTime", filterTime);

        // 3. 다음 단계로 전달
        filterChain.doFilter(request, response);
    }
}