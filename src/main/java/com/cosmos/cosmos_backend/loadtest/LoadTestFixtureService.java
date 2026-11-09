package com.cosmos.cosmos_backend.loadtest;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.jwt.JwtTokenProvider;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class LoadTestFixtureService {

    private static final int BATTLE_USER_COUNT = 100;
    private static final String USERNAME_PREFIX = "loadtest-b0-";

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public List<LoadTestUserCredential> prepareBattleUsers() {
        List<LoadTestUserCredential> credentials = new ArrayList<>(BATTLE_USER_COUNT);

        for (int sequence = 1; sequence <= BATTLE_USER_COUNT; sequence++) {
            String username = USERNAME_PREFIX + String.format("%03d", sequence);
            User user = userRepository.findFirstByUsername(username)
                    .orElseGet(() -> userRepository.save(new User(username, null)));

            credentials.add(new LoadTestUserCredential(
                    user.getId(),
                    jwtTokenProvider.createAccessToken(user.getId(), user.getUsername())
            ));
        }

        return credentials;
    }
}
