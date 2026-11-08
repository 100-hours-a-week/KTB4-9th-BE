package com.cosmos.cosmos_backend.auth.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class WithdrawalCleanupRepository {

    @PersistenceContext
    private EntityManager entityManager;

    // 사용자와 연결된 모든 데이터를 영구 삭제
    // 외래키 제약을 위반하지 않도록 자식 데이터를 먼저 삭제하고 users를 마지막에 삭제
    public void deleteAllByUserId(Long userId) {

        // 자연어 풀이 평가 알림 및 평가 작업 삭제
        delete("DELETE FROM notifications WHERE user_id = :userId", userId);
        delete("DELETE FROM solution_evaluation_tasks WHERE user_id = :userId", userId);

        // 배틀 참여 기록을 참조하는 이벤트와 제출 답안 삭제
        delete("""
                DELETE FROM outbox_events
                WHERE participation_id IN (
                    SELECT participation_id
                    FROM battle_participations
                    WHERE user_id = :userId
                )
                """, userId);

        delete("""
                DELETE FROM battle_submissions
                WHERE participation_id IN (
                    SELECT participation_id
                    FROM battle_participations
                    WHERE user_id = :userId
                )
                """, userId);

        // 사용자 배틀 참여 및 알림 기록 삭제
        delete("DELETE FROM battle_participations WHERE user_id = :userId", userId);
        delete("DELETE FROM battle_alarms WHERE user_id = :userId", userId);

        // 문제 풀이 및 학습 기록 삭제
        delete("DELETE FROM saved_problems WHERE user_id = :userId", userId);
        delete("DELETE FROM used_hints WHERE user_id = :userId", userId);
        delete("DELETE FROM daily_generated_counts WHERE user_id = :userId", userId);
        delete("DELETE FROM code_submissions WHERE user_id = :userId", userId);
        delete("DELETE FROM problem_solution_submissions WHERE user_id = :userId", userId);
        delete("DELETE FROM activities WHERE user_id = :userId", userId);

        // 랭킹 및 포인트 기록 삭제
        delete("DELETE FROM user_category_points WHERE user_id = :userId", userId);
        delete("DELETE FROM user_difficulty_points WHERE user_id = :userId", userId);
        delete("DELETE FROM user_points WHERE user_id = :userId", userId);

        // 인증 정보 삭제
        delete("DELETE FROM refresh_tokens WHERE user_id = :userId", userId);
        delete("DELETE FROM user_oauth_accounts WHERE user_id = :userId", userId);

        // 모든 연결 데이터 삭제 후 사용자 계정 최종 삭제
        delete("DELETE FROM users WHERE id = :userId", userId);
    }

    // 사용자 ID를 바인딩하여 삭제 쿼리 실행
    private void delete(String sql, Long userId) {
        entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .executeUpdate();
    }
}