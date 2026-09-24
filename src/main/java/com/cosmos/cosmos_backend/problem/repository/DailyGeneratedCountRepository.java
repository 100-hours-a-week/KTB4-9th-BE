package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.DailyGeneratedCount;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyGeneratedCountRepository extends JpaRepository<DailyGeneratedCount, Long> {

    // 오늘 사용 횟수를 숫자로만 조회 (엔티티를 영속성 컨텍스트에 올리지 않기 위해 값만 꺼냄)
    @Query("select c.generationCount from DailyGeneratedCount c where c.userId = :userId and c.usageDate = :usageDate")
    Optional<Integer> findCount(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    // 행을 잠그고 조회 (트랜잭션 안에서만 호출 가능)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DailyGeneratedCount> findForUpdateByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
