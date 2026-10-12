package com.cosmos.cosmos_backend.approach.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.domain.KeywordResult;
import com.cosmos.cosmos_backend.common.Category;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class ApproachSubmissionRepositoryTest {

    @Autowired
    private ApproachSubmissionRepository approachSubmissionRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void keywordResults_surviveSaveAndReload() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(7L, 1L, Category.ARRAY, "풀이", true, 90, "좋아요");
        submission.updateKeywordResults(List.of(new KeywordResult("해시맵", true), new KeywordResult("순회", false)));
        em.persist(submission);
        em.flush();
        em.clear();

        // When
        ApproachSubmission loaded = approachSubmissionRepository.findByUserIdAndProblemId(7L, 1L).orElseThrow();

        // Then
        assertThat(loaded.getKeywordResults())
                .containsExactly(new KeywordResult("해시맵", true), new KeywordResult("순회", false));
    }

    @Test
    void keywordResults_areNull_whenNeverSaved() {
        // Given
        em.persist(new ApproachSubmission(7L, 2L, Category.ARRAY, "풀이", false, 10, "아쉬워요"));
        em.flush();
        em.clear();

        // When
        ApproachSubmission loaded = approachSubmissionRepository.findByUserIdAndProblemId(7L, 2L).orElseThrow();

        // Then
        assertThat(loaded.getKeywordResults()).isNull();
    }
}
