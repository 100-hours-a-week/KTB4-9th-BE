package com.cosmos.cosmos_backend.dailyBattle.service;


import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BattleGradingService {

    private final BattleParticipationRepository battleParticipationRepository;


    // 작업 선점 트랜잭션
    @Transactional
    public Optional<Long> preemptSubmission(){

        // 1. 작업 조회 + 락 획득
        Optional<BattleParticipation> optional = battleParticipationRepository.findSubmittedForUpdate();

        // 2. 작업이 없으면 종료
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        // 3. 채점 중으로 상태 변경
        BattleParticipation preemptedParticipation = optional.get();
        preemptedParticipation.grading();

        // 4. 참여 id 반환
        return Optional.of(preemptedParticipation.getParticipationId());

    }

    // TODO: 채점 메소드
    @Transactional
    public void battleGrade(Long participationId) {

    }
}
