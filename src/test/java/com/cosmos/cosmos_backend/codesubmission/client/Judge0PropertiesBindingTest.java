package com.cosmos.cosmos_backend.codesubmission.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.common.Language;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** application.yaml / application-prod.yaml의 Judge0 설정이 의도대로 읽히는지 확인 (환경변수는 쓰지 않음). */
class Judge0PropertiesBindingTest {

    // yaml 파일들을 우선순위 순서(앞이 우선)로 읽어서 Judge0Properties로 바인딩
    private Judge0Properties bind(String... files) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        for (String file : files) {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
            loaded.forEach(sources::addLast);
        }
        Binder binder = new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources));
        return binder.bind("judge0", Judge0Properties.class).get();
    }

    @Test
    void commonConfig_pointsToLocalhost_soLocalAndTestNeverCallRealServer() throws IOException {
        Judge0Properties properties = bind("application.yaml");

        assertThat(properties.baseUrl()).isEqualTo("http://localhost:2358");
    }

    @Test
    void prodConfig_overridesOnlyBaseUrlWithDevServer() throws IOException {
        Judge0Properties properties = bind("application-prod.yaml", "application.yaml");

        assertThat(properties.baseUrl()).isEqualTo("https://dev-judge.cosmoscode.site");
        // 나머지는 공통 설정이 그대로 적용됨
        assertThat(properties.batchSize()).isEqualTo(20);
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void commonConfig_hasLimitsAndLanguageIds() throws IOException {
        Judge0Properties properties = bind("application.yaml");

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.maxWait()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.batchSize()).isEqualTo(20);
        assertThat(properties.maxMemoryKb()).isEqualTo(512000);
        assertThat(properties.languageIds()).containsOnly(
                java.util.Map.entry(Language.PYTHON, 71),
                java.util.Map.entry(Language.JAVASCRIPT, 63),
                java.util.Map.entry(Language.JAVA, 62),
                java.util.Map.entry(Language.CPP, 54)
        );
    }
}
