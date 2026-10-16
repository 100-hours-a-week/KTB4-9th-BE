package com.cosmos.cosmos_backend.savedproblem.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SavedProblemStatusTest {

    @Test
    void from_returnsNotSubmitted_whenNoSubmission() {
        assertThat(SavedProblemStatus.from(null)).isEqualTo(SavedProblemStatus.NOT_SUBMITTED);
    }

    @Test
    void from_returnsInProgress_whenNotSolved() {
        assertThat(SavedProblemStatus.from(false)).isEqualTo(SavedProblemStatus.IN_PROGRESS);
    }

    @Test
    void from_returnsCorrect_whenSolved() {
        assertThat(SavedProblemStatus.from(true)).isEqualTo(SavedProblemStatus.CORRECT);
    }
}
