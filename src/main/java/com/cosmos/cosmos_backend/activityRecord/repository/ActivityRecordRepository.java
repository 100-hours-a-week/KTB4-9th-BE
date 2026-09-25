package com.cosmos.cosmos_backend.activityRecord.repository;

import com.cosmos.cosmos_backend.activityRecord.domain.entity.ActivityRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {
    // 기간 동안의 학습 기록
    List<ActivityRecord> findByUserIdAndActivityDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    // 날짜에 대한 유저의 기록
    Optional<ActivityRecord> findByUserIdAndActivityDate(Long userId, LocalDate activityDate);
}
