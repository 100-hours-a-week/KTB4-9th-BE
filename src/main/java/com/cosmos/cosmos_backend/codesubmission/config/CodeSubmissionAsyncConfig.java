package com.cosmos.cosmos_backend.codesubmission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** 코드 제출 전용 가상 스레드 실행기 설정 (전역 스레드 정책은 바꾸지 않음). */
@Configuration
public class CodeSubmissionAsyncConfig {

    // 코드 제출 채점을 실행할 가상 스레드 실행기
    @Bean
    public AsyncTaskExecutor codeSubmissionExecutor() {
        // 1. 작업마다 가상 스레드를 만드는 실행기 (스레드 이름: code-submission-N)
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("code-submission-");
        executor.setVirtualThreads(true);

        // 2. 앱 종료 시 진행 중인 채점을 최대 60초 기다린 뒤 종료
        executor.setTaskTerminationTimeout(60_000);
        return executor;
    }
}
