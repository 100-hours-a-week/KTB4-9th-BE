package com.cosmos.cosmos_backend.ActivityRecord.repository;

import com.cosmos.cosmos_backend.ActivityRecord.domain.entity.ActivityRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {
    // 기간 동안의 학습 기록
    List<ActivityRecord> findByUserIdAndActivityDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

}
