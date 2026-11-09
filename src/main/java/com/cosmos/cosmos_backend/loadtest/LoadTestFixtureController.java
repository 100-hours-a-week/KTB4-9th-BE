package com.cosmos.cosmos_backend.loadtest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@Profile("dev")
@RequestMapping("/internal/load-test")
public class LoadTestFixtureController {

    private final LoadTestFixtureService fixtureService;
    private final boolean enabled;
    private final String expectedSecret;

    public LoadTestFixtureController(
            LoadTestFixtureService fixtureService,
            @Value("${loadtest.fixtures.enabled:false}") boolean enabled,
            @Value("${loadtest.fixtures.secret:}") String expectedSecret
    ) {
        this.fixtureService = fixtureService;
        this.enabled = enabled;
        this.expectedSecret = expectedSecret;
    }

    @PostMapping("/battle/users")
    public ResponseEntity<List<LoadTestUserCredential>> prepareBattleUsers(
            @RequestHeader(value = "X-Load-Test-Secret", required = false) String providedSecret
    ) {
        verifyAccess(providedSecret);
        return ResponseEntity.ok(fixtureService.prepareBattleUsers());
    }

    private void verifyAccess(String providedSecret) {
        if (!enabled || expectedSecret.isBlank() || providedSecret == null) {
            throw notFound();
        }

        boolean matches = MessageDigest.isEqual(
                expectedSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw notFound();
        }
    }

    private ResponseStatusException notFound() {
        // 엔드포인트 활성화 여부와 비밀키 검증 결과를 외부에 노출하지 않는다.
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
