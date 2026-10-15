package com.cosmos.cosmos_backend.battlealarm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cosmos.cosmos_backend.battlealarm.domain.BattleAlarm;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class BattleAlarmRepositoryTest {

    @Autowired
    private BattleAlarmRepository battleAlarmRepository;

    @Autowired
    private DailyBattleRepository dailyBattleRepository;

    @Autowired
    private TestEntityManager em;

    private BattleAlarm dismissedAlarm(Long userId, Long battleId) {
        BattleAlarm alarm = new BattleAlarm(userId, battleId);
        alarm.dismiss();
        return em.persist(alarm);
    }

    @Test
    void findByBattleDate_returnsBattle_whenBattleExistsOnThatDate() {
        // Given: 오늘 날짜의 배틀
        LocalDate today = LocalDate.of(2026, 10, 13);
        DailyBattle battle = em.persist(new DailyBattle(today, Category.ARRAY, "소수 판별", "내용"));
        em.flush();
        em.clear();

        // When
        var found = dailyBattleRepository.findByBattleDate(today);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getBattleId()).isEqualTo(battle.getBattleId());
    }

    @Test
    void findByBattleDate_returnsEmpty_whenNoBattleOnThatDate() {
        // Given: 다른 날짜의 배틀만 있음
        em.persist(new DailyBattle(LocalDate.of(2026, 10, 12), Category.ARRAY, "어제 배틀", "내용"));
        em.flush();
        em.clear();

        // When & Then
        assertThat(dailyBattleRepository.findByBattleDate(LocalDate.of(2026, 10, 13))).isEmpty();
    }

    @Test
    void existsDismissed_returnsTrueOnlyForUserWhoDismissed() {
        // Given: 7번 사용자만 3번 배틀 알림을 닫음
        dismissedAlarm(7L, 3L);
        em.flush();
        em.clear();

        // When & Then
        assertThat(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).isTrue();
        assertThat(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(8L, 3L)).isFalse();
    }

    @Test
    void existsDismissed_returnsFalse_whenRowExistsButNotDismissed() {
        // Given: 행은 있지만 닫지 않은 상태
        em.persist(new BattleAlarm(7L, 3L));
        em.flush();
        em.clear();

        // When & Then
        assertThat(battleAlarmRepository.existsByUserIdAndBattleIdAndDismissedTrue(7L, 3L)).isFalse();
    }

    @Test
    void findByUserIdAndBattleId_returnsRow_onlyForMatchingUserAndBattle() {
        // Given
        dismissedAlarm(7L, 3L);
        em.flush();
        em.clear();

        // When & Then
        assertThat(battleAlarmRepository.findByUserIdAndBattleId(7L, 3L)).isPresent();
        assertThat(battleAlarmRepository.findByUserIdAndBattleId(7L, 4L)).isEmpty();
        assertThat(battleAlarmRepository.findByUserIdAndBattleId(8L, 3L)).isEmpty();
    }

    @Test
    void save_throws_whenSameUserAndBattleSavedTwice() {
        // Given: 이미 저장된 사용자·배틀 조합
        em.persist(new BattleAlarm(7L, 3L));
        em.flush();

        // When & Then: 같은 조합을 한 번 더 저장하면 유니크 제약 예외
        assertThatThrownBy(() -> {
            em.persist(new BattleAlarm(7L, 3L));
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void dismissed_defaultsToFalse_whenInsertedWithoutValue() {
        // Given: dismissed 값을 넣지 않고 DB에 직접 저장
        em.getEntityManager()
                .createNativeQuery("insert into battle_alarms (battle_id, user_id) values (3, 7)")
                .executeUpdate();
        em.clear();

        // When
        var found = battleAlarmRepository.findByUserIdAndBattleId(7L, 3L);

        // Then: DB 기본값 FALSE가 적용됨
        assertThat(found).isPresent();
        assertThat(found.get().isDismissed()).isFalse();
    }
}
