package com.cosmos.cosmos_backend.auth.repository;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 테스트용
    Optional<User> findFirstByUsername(String username);

}
