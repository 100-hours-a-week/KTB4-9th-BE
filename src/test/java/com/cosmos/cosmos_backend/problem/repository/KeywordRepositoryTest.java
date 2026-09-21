package com.cosmos.cosmos_backend.problem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.problem.domain.Keyword;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class KeywordRepositoryTest {

    @Autowired
    private KeywordRepository keywordRepository;

    @Test
    void findByProblemIdOrderById_returnsOnlyThatProblemKeywordsInSavedOrder() {
        // Given
        keywordRepository.save(new Keyword(1L, "점화식"));
        keywordRepository.save(new Keyword(2L, "해시맵"));
        keywordRepository.save(new Keyword(1L, "모듈러 연산"));
        keywordRepository.flush();

        // When
        var keywords = keywordRepository.findByProblemIdOrderById(1L);

        // Then
        assertThat(keywords).extracting(Keyword::getKeyword).containsExactly("점화식", "모듈러 연산");
    }

    @Test
    void findByProblemIdOrderById_returnsEmpty_whenNoKeywords() {
        assertThat(keywordRepository.findByProblemIdOrderById(999L)).isEmpty();
    }
}
