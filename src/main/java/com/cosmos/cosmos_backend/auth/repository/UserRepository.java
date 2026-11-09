package com.cosmos.cosmos_backend.auth.repository;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 테스트용
    Optional<User> findFirstByUsername(String username);

    // 탈퇴 신청 후 30일이 지난 사용자 ID 최대 100명 조회
    @Query(value = """
        SELECT id
        FROM users
        WHERE deleted_at IS NOT NULL
          AND deleted_at <= :cutoff
        ORDER BY id
        LIMIT 100
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> findExpiredWithdrawalUserIdsForUpdate(
            @Param("cutoff") LocalDateTime cutoff
    );

}
