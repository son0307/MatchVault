package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.news.client.OpenAiTitleTranslationClient;
import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.entity.TeamNewsArticle;
import com.son.soccerStreaming.news.repository.TeamNewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNewsTranslationServiceTest {

    @Mock TeamNewsArticleRepository relationRepository;
    @Mock OpenAiTitleTranslationClient translationClient;
    @Mock NewsTranslationPersistenceService persistenceService;

    @Test
    void translatesOnlyTheRequestedTeamArticle() {
        NewsArticle article = NewsArticle.builder().id(7L).originalTitle("Original title").build();
        when(relationRepository.findByTeamIdAndArticleId(42L, 7L))
                .thenReturn(Optional.of(TeamNewsArticle.builder().article(article).build()));
        when(translationClient.translate(any())).thenReturn(Map.of(7L, "번역 제목"));

        var result = service().translate(42L, 7L);

        assertThat(result).isEqualTo(new AdminNewsTranslationService.TranslationResult(7L, "번역 제목"));
        verify(persistenceService).applyResults(List.of(7L), Map.of(7L, "번역 제목"));
    }

    @Test
    void returnsExistingTranslationWithoutCallingOpenAi() {
        NewsArticle article = NewsArticle.builder()
                .id(7L)
                .originalTitle("Original title")
                .translatedTitle("기존 번역")
                .build();
        when(relationRepository.findByTeamIdAndArticleId(42L, 7L))
                .thenReturn(Optional.of(TeamNewsArticle.builder().article(article).build()));

        assertThat(service().translate(42L, 7L).translatedTitle()).isEqualTo("기존 번역");
        verifyNoInteractions(translationClient, persistenceService);
    }

    @Test
    void rejectsArticleThatIsNotConnectedToTheTeam() {
        when(relationRepository.findByTeamIdAndArticleId(42L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().translate(42L, 7L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NEWS_ARTICLE_NOT_FOUND));
    }

    @Test
    void sanitizesTranslationClientFailure() {
        NewsArticle article = NewsArticle.builder().id(7L).originalTitle("Original title").build();
        when(relationRepository.findByTeamIdAndArticleId(42L, 7L))
                .thenReturn(Optional.of(TeamNewsArticle.builder().article(article).build()));
        when(translationClient.translate(any())).thenThrow(new IllegalStateException("provider details"));

        assertThatThrownBy(() -> service().translate(42L, 7L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NEWS_TRANSLATION_FAILED));
    }

    private AdminNewsTranslationService service() {
        return new AdminNewsTranslationService(relationRepository, translationClient, persistenceService);
    }
}
