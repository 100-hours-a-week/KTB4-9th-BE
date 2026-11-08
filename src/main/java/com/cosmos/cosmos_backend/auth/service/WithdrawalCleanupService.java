package com.cosmos.cosmos_backend.auth.service;

import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.auth.repository.WithdrawalCleanupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalCleanupService {

    private static final int RETENTION_DAYS = 30;

    private final UserRepository userRepository;

    private final WithdrawalCleanupRepository cleanupRepository;

    private final Clock clock;

    // 탈퇴 신청 후 30일이 지난 사용자를 최대 100명씩 영구 삭제
    @Transactional
    public int deleteExpiredUsers() {

        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);

        List<Long> userIds = userRepository.findExpiredWithdrawalUserIdsForUpdate(cutoff);

        for (Long userId : userIds) {
            cleanupRepository.deleteAllByUserId(userId);
        }

        return userIds.size();
    }
}