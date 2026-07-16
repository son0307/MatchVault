package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsTranslationPersistenceServiceTest {

    @Mock NewsArticleRepository repository;

    @Test
    void rejectsMissingEmptyAndUnknownIdsWithoutCorruptingMapping() {
        NewsArticle first = article(1L);
        NewsArticle second = article(2L);
        when(repository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));
        NewsTranslationPersistenceService service = new NewsTranslationPersistenceService(repository);

        service.applyResults(List.of(1L, 2L), Map.of(1L, "번역 제목", 999L, "잘못된 ID"));

        assertThat(first.getTranslatedTitle()).isEqualTo("번역 제목");
        assertThat(second.getTranslatedTitle()).isNull();
    }

    @Test
    void marksArticlesBeforeTheirOnlyAutomaticTranslationAttempt() {
        NewsArticle article = article(1L);
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(article));

        new NewsTranslationPersistenceService(repository).markAutoTranslationAttempted(List.of(1L));

        assertThat(article.getAutoTranslationAttempted()).isTrue();
    }

    private NewsArticle article(Long id) {
        return NewsArticle.builder()
                .id(id)
                .originalTitle("Title " + id)
                .build();
    }
}
