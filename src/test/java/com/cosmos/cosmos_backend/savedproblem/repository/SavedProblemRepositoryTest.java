package com.cosmos.cosmos_backend.savedproblem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.dto.SavedProblemRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class SavedProblemRepositoryTest {

    @Autowired
    private SavedProblemRepository savedProblemRepository;

    @Autowired
    private TestEntityManager em;

    private Problem saveProblem(Difficulty difficulty, String title) {
        return em.persist(new Problem(difficulty, Category.ARRAY, title, "내용", "입력", "출력", List.of(), "배열"));
    }

    @Test
    void findSavedProblems_includesProblemsWithoutSubmission_andOnlyMySubmission() {
        // Given: 7번 사용자가 문제 3개를 저장. 1번은 정답, 2번은 풀이 중, 3번은 제출 없음. 8번 사용자도 1번에 제출
        Problem solved = saveProblem(Difficulty.LV1, "정답 문제");
        Problem trying = saveProblem(Difficulty.LV2, "풀이 중 문제");
        Problem none = saveProblem(Difficulty.LV3, "미제출 문제");
        em.persist(new SavedProblem(7L, solved.getId()));
        em.persist(new SavedProblem(7L, trying.getId()));
        em.persist(new SavedProblem(7L, none.getId()));
        ApproachSubmission mine = new ApproachSubmission(7L, solved.getId(), Category.ARRAY, "내 풀이", true, 90, "좋아요");
        mine.markAsSolved();
        em.persist(mine);
        em.persist(new ApproachSubmission(7L, trying.getId(), Category.ARRAY, "시도한 풀이", false, 40, "아쉬워요"));
        em.persist(new ApproachSubmission(8L, none.getId(), Category.ARRAY, "남의 풀이", true, 100, "좋아요"));
        em.flush();

        // When
        List<SavedProblemRow> rows = savedProblemRepository.findSavedProblems(7L, null);

        // Then: 제출이 없는 문제도 목록에 있고, 다른 사용자의 풀이는 섞이지 않음
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(SavedProblemRow::title).containsExactlyInAnyOrder("정답 문제", "풀이 중 문제", "미제출 문제");
        SavedProblemRow solvedRow = rows.stream().filter(r -> r.title().equals("정답 문제")).findFirst().orElseThrow();
        SavedProblemRow tryingRow = rows.stream().filter(r -> r.title().equals("풀이 중 문제")).findFirst().orElseThrow();
        SavedProblemRow noneRow = rows.stream().filter(r -> r.title().equals("미제출 문제")).findFirst().orElseThrow();
        assertThat(solvedRow.isSolved()).isTrue();
        assertThat(solvedRow.naturalSolution()).isEqualTo("내 풀이");
        assertThat(tryingRow.isSolved()).isFalse();
        assertThat(noneRow.isSolved()).isNull();
        assertThat(noneRow.naturalSolution()).isNull();
    }

    @Test
    void findSavedProblems_filtersByDifficulty() {
        // Given
        Problem easy = saveProblem(Difficulty.LV1, "쉬운 문제");
        Problem hard = saveProblem(Difficulty.LV4, "어려운 문제");
        em.persist(new SavedProblem(7L, easy.getId()));
        em.persist(new SavedProblem(7L, hard.getId()));
        em.flush();

        // When
        List<SavedProblemRow> rows = savedProblemRepository.findSavedProblems(7L, Difficulty.LV4);

        // Then
        assertThat(rows).extracting(SavedProblemRow::title).containsExactly("어려운 문제");
    }
}
