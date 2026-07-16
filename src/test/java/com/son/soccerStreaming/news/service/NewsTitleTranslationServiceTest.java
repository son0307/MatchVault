package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.client.OpenAiTitleTranslationClient;
import com.son.soccerStreaming.news.config.NewsProperties;
import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsTitleTranslationServiceTest {

    @Mock NewsArticleRepository newsArticleRepository;
    @Mock OpenAiTitleTranslationClient translationClient;
    @Mock NewsTranslationPersistenceService persistenceService;
    private NewsProperties properties;
    private NewsTitleTranslationService service;

    @BeforeEach
    void setUp() {
        properties = new NewsProperties();
        properties.getTranslation().setBatchSize(50);
        service = new NewsTitleTranslationService(newsArticleRepository, translationClient, persistenceService, properties);
    }

    @Test
    void appliesOnlyRequestedIdsAndCountsMissingOrEmptyResultsAsFailures() {
        NewsArticle first = article(1L, "First");
        NewsArticle second = article(2L, "Second");
        when(newsArticleRepository.findTranslationCandidates()).thenReturn(List.of(first, second));
        when(translationClient.translate(any())).thenReturn(Map.of(
                1L, "첫 번째",
                2L, "   ",
                999L, "요청하지 않은 결과"
        ));

        var result = service.translatePending();

        assertThat(result).isEqualTo(new NewsTitleTranslationService.TranslationRunResult(2, 1, 1));
        verify(persistenceService).markAutoTranslationAttempted(List.of(1L, 2L));
        verify(persistenceService).applyResults(List.of(1L, 2L), Map.of(
                1L, "첫 번째", 2L, "   ", 999L, "요청하지 않은 결과"));
        verify(newsArticleRepository).findTranslationCandidates();
    }

    @Test
    void leavesWholeBatchUntranslatedWhenClientCallFails() {
        NewsArticle first = article(1L, "First");
        when(newsArticleRepository.findTranslationCandidates()).thenReturn(List.of(first));
        when(translationClient.translate(any())).thenThrow(new IllegalStateException("invalid response"));

        var result = service.translatePending();

        assertThat(result).isEqualTo(new NewsTitleTranslationService.TranslationRunResult(1, 0, 1));
        verify(persistenceService).markAutoTranslationAttempted(List.of(1L));
    }

    @Test
    void selectsTranslationCandidatesOnlyForRequestedTeam() {
        when(newsArticleRepository.findTranslationCandidatesByTeamId(42L)).thenReturn(List.of());

        var result = service.translatePendingForTeam(42L);

        assertThat(result).isEqualTo(new NewsTitleTranslationService.TranslationRunResult(0, 0, 0));
        verify(newsArticleRepository).findTranslationCandidatesByTeamId(42L);
    }

    private NewsArticle article(Long id, String title) {
        return NewsArticle.builder()
                .id(id)
                .originalTitle(title)
                .build();
    }
}
