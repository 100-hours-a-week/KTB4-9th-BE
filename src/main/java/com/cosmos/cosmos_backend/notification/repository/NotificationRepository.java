package com.cosmos.cosmos_backend.notification.repository;

import com.cosmos.cosmos_backend.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 문제별 최신 작업에 연결된 알림만 최신순으로 (읽음 포함)
    @Query("""
            select n from Notification n
            where n.userId = :userId
              and n.evaluationTaskId = (
                  select max(t.id) from SolutionEvaluationTask t
                  where t.userId = n.userId and t.problemId = n.problemId)
            order by n.id desc
            """)
    List<Notification> findOfLatestTasks(@Param("userId") Long userId, Limit limit);

    // 위와 같은 조건에서 안 읽은 알림 전체 개수
    @Query("""
            select count(n) from Notification n
            where n.userId = :userId and n.readAt is null
              and n.evaluationTaskId = (
                  select max(t.id) from SolutionEvaluationTask t
                  where t.userId = n.userId and t.problemId = n.problemId)
            """)
    long countUnreadOfLatestTasks(@Param("userId") Long userId);

    // 평가 작업에 연결된 알림 (작업당 1건)
    Optional<Notification> findByEvaluationTaskId(Long evaluationTaskId);

    // 알림 id와 사용자 id로 조회 (남의 알림은 조회되지 않음)
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
