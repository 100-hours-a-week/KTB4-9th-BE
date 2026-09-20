package com.cosmos.cosmos_backend.auth.repository;

import com.cosmos.cosmos_backend.auth.domain.entity.RefreshToken;
import com.cosmos.cosmos_backend.auth.domain.entity.UserOauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByRefreshTokenHash(String refreshTokenHash);
}
