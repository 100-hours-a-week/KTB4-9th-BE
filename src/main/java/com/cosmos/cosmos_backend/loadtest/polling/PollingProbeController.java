package com.cosmos.cosmos_backend.loadtest.polling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated local diagnostic API; additionally requires a key derived from the local JWT secret. */
@RestController
@Profile("local")
@RequestMapping("/internal/load-test/polling")
public class PollingProbeController {
    private final PollingTelemetry telemetry;
    private final byte[] expectedKey;
    public PollingProbeController(PollingTelemetry telemetry, @Value("${jwt.secret}") String secret) throws Exception {
        this.telemetry = telemetry;
        expectedKey = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("polling-probe:" + secret).getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
    }
    private void authorize(String key) {
        if (!MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    @GetMapping("/clock")
    public Map<String, Object> clock(@RequestHeader("X-Loadtest-Key") String key) {
        authorize(key);
        return Map.of("serverTimeMs", System.currentTimeMillis(), "run", telemetry.run());
    }
    @PostMapping("/begin")
    public void begin(@RequestHeader("X-Loadtest-Key") String key, @RequestParam String run) {
        authorize(key); telemetry.begin(run);
    }
    @PostMapping("/end")
    public void end(@RequestHeader("X-Loadtest-Key") String key) { authorize(key); telemetry.end(); }
    @GetMapping("/completions")
    public Map<Long, PollingTelemetry.Completion> completions(@RequestHeader("X-Loadtest-Key") String key) {
        authorize(key); return telemetry.completions();
    }
}
