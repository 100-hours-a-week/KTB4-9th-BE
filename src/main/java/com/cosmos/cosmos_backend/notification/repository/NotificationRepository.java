package com.cosmos.cosmos_backend.notification.repository;

import com.cosmos.cosmos_backend.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 사용자의 최근 알림 10개 (읽음 포함, 최신순)
    List<Notification> findTop10ByUserIdOrderByIdDesc(Long userId);

    // 사용자의 안 읽은 알림 전체 개수
    long countByUserIdAndReadAtIsNull(Long userId);

    // 알림 id와 사용자 id로 조회 (남의 알림은 조회되지 않음)
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
