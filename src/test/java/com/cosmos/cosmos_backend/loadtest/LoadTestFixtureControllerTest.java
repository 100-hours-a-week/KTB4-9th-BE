package com.cosmos.cosmos_backend.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class LoadTestFixtureControllerTest {

    private final LoadTestFixtureService fixtureService = mock(LoadTestFixtureService.class);

    @Test
    void disabledFixtureIsHidden() {
        LoadTestFixtureController controller = new LoadTestFixtureController(
                fixtureService, false, "expected-secret");

        assertThatThrownBy(() -> controller.prepareBattleUsers("expected-secret", 100))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void wrongSecretIsHidden() {
        LoadTestFixtureController controller = new LoadTestFixtureController(
                fixtureService, true, "expected-secret");

        assertThatThrownBy(() -> controller.prepareBattleUsers("wrong-secret", 100))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void correctSecretReturnsPreparedCredentials() {
        LoadTestFixtureController controller = new LoadTestFixtureController(
                fixtureService, true, "expected-secret");
        List<LoadTestUserCredential> credentials = List.of(
                new LoadTestUserCredential(1L, "access-token"));
        when(fixtureService.prepareBattleUsers(300)).thenReturn(credentials);

        ResponseEntity<List<LoadTestUserCredential>> response =
                controller.prepareBattleUsers("expected-secret", 300);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(credentials);
        verify(fixtureService).prepareBattleUsers(300);
    }

    @Test
    void userCountOverLimitIsRejected() {
        LoadTestFixtureController controller = new LoadTestFixtureController(
                fixtureService, true, "expected-secret");

        assertThatThrownBy(() -> controller.prepareBattleUsers("expected-secret", 1_001))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
