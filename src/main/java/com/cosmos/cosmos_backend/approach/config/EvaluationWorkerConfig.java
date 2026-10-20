package com.cosmos.cosmos_backend.approach.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EvaluationWorkerProperties.class)
public class EvaluationWorkerConfig {
}
