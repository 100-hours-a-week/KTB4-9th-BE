package com.cosmos.cosmos_backend.auth.repository;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

}
