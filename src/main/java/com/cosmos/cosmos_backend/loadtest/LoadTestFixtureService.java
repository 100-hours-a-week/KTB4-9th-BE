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
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class LoadTestFixtureService {

    private static final int MAX_BATTLE_USER_COUNT = 1_000;
    private static final String USERNAME_PREFIX = "loadtest-b0-";

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public List<LoadTestUserCredential> prepareBattleUsers(int userCount) {
        if (userCount < 1 || userCount > MAX_BATTLE_USER_COUNT) {
            throw new IllegalArgumentException("userCount must be between 1 and 1000");
        }

        List<LoadTestUserCredential> credentials = new ArrayList<>(userCount);

        for (int sequence = 1; sequence <= userCount; sequence++) {
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
