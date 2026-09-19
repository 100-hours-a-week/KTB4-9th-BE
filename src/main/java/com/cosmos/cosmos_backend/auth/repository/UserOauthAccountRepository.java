package com.cosmos.cosmos_backend.auth.repository;

import com.cosmos.cosmos_backend.auth.domain.OAuthProvider;
import com.cosmos.cosmos_backend.auth.domain.entity.UserOauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOauthAccountRepository extends JpaRepository<UserOauthAccount, Long> {

    // 헤딩 provider와 providerUserId를 가진 사용자가 존재하는지
    Optional<UserOauthAccount> findByOauthProviderAndProviderUserId (OAuthProvider oauthProvider, String providerUserId);
}