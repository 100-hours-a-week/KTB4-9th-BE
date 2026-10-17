package com.cosmos.cosmos_backend.dailyBattle.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "battle_submissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_participation_battle_case",
                columnNames = {"participation_id", "battle_case_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "battle_submission_id")
    private Long battleSubmissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participation_id",  nullable = false)
    private BattleParticipation participation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_case_id", nullable = false)
    private BattleCase battleCase;

    @Column(name = "submitted_output", nullable = false)
    private String submittedOutput;

    @Column(name = "is_correct", nullable = true)
    private Boolean isCorrect;

    public BattleSubmission(BattleParticipation participation, BattleCase battleCase, String submittedOutput) {
        this.participation = participation;
        this.battleCase = battleCase;
        this.submittedOutput = submittedOutput;
    }

    // 정답 처리
    public void answerCorrect(){
        this.isCorrect = true;
    }

    // 오답 처리
    public void answerIncorrect(){
        this.isCorrect = false;
    }

}
