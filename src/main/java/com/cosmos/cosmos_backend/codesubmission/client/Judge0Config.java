package com.cosmos.cosmos_backend.codesubmission.client;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(Judge0Properties.class)
public class Judge0Config {

    /** Judge0 서버 호출용 RestClient. 연결/응답 타임아웃을 설정에서 읽음. */
    @Bean
    public RestClient judge0RestClient(Judge0Properties properties) {
        // 1. 연결 타임아웃을 설정한 HTTP 클라이언트를 생성 (HTTP/1.1로 고정)
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();

        // 2. 응답 타임아웃을 설정
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        // 3. Judge0 서버 주소(base-url)가 설정된 RestClient를 반환
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
