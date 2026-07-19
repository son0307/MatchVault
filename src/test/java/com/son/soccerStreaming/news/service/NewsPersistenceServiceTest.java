package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.client.SerpApiNewsClient;
import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.entity.TeamNewsArticle;
import com.son.soccerStreaming.news.entity.TeamNewsCollectionState;
import com.son.soccerStreaming.news.repository.NewsArticleRepository;
import com.son.soccerStreaming.news.repository.TeamNewsArticleRepository;
import com.son.soccerStreaming.news.repository.TeamNewsCollectionStateRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsPersistenceServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock NewsArticleRepository articleRepository;
    @Mock TeamNewsArticleRepository relationRepository;
    @Mock TeamNewsCollectionStateRepository collectionStateRepository;
    @Mock SerpApiNewsClient serpApiNewsClient;

    @Test
    void reusesOneTranslatedArticleAcrossDifferentTeams() {
        Team first = Team.builder().teamId(1L).name("Arsenal").build();
        Team second = Team.builder().teamId(2L).name("Chelsea").build();
        NewsArticle existing = NewsArticle.builder()
                .id(7L)
                .urlHash("hash")
                .originalUrl("https://bbc.com/sport/football/articles/shared")
                .originalTitle("Shared title")
                .translatedTitle("공통 제목")
                .publisherName("BBC Sport")
                .publisherDomain("bbc.com")
                .build();
        var item = new SerpApiNewsClient.SearchArticle(
                "Shared title",
                "https://bbc.com/sport/football/articles/shared?utm_source=test",
                "BBC Sport",
                Instant.parse("2026-07-14T01:00:00Z")
        );
        when(teamRepository.findByTeamId(1L)).thenReturn(Optional.of(first));
        when(teamRepository.findByTeamId(2L)).thenReturn(Optional.of(second));
        when(articleRepository.findByUrlHash(any())).thenReturn(Optional.of(existing));
        when(articleRepository.save(existing)).thenReturn(existing);
        when(serpApiNewsClient.publisherDomain(any())).thenReturn("bbc.com");
        when(relationRepository.findByTeamAndArticle(any(), any())).thenReturn(Optional.empty());

        NewsPersistenceService service = new NewsPersistenceService(
                teamRepository, articleRepository, relationRepository, collectionStateRepository, serpApiNewsClient);
        service.saveTeamArticles(1L, List.of(item), Instant.now());
        service.saveTeamArticles(2L, List.of(item), Instant.now());

        assertThat(existing.getTranslatedTitle()).isEqualTo("공통 제목");
        verify(articleRepository, times(2)).save(existing);
        verify(relationRepository, times(2)).save(any());
    }

    @Test
    void preservesTranslationWhenRecollectedTitleOnlyDiffersByWhitespace() {
        Team team = Team.builder().teamId(1L).name("Arsenal").build();
        NewsArticle existing = NewsArticle.builder()
                .id(7L)
                .urlHash("hash")
                .originalUrl("https://bbc.com/sport/football/articles/shared")
                .originalTitle("Arsenal complete major signing")
                .translatedTitle("아스널, 대형 영입 완료")
                .autoTranslationAttempted(true)
                .publisherName("BBC Sport")
                .publisherDomain("bbc.com")
                .build();
        var item = new SerpApiNewsClient.SearchArticle(
                "  Arsenal   complete\u00a0major signing  ",
                existing.getOriginalUrl(),
                "BBC Sport",
                Instant.parse("2026-07-14T01:00:00Z")
        );
        when(teamRepository.findByTeamId(1L)).thenReturn(Optional.of(team));
        when(articleRepository.findByUrlHash(any())).thenReturn(Optional.of(existing));
        when(articleRepository.save(existing)).thenReturn(existing);
        when(serpApiNewsClient.publisherDomain(any())).thenReturn("bbc.com");
        when(relationRepository.findByTeamAndArticle(any(), any())).thenReturn(Optional.empty());

        service().saveTeamArticles(1L, List.of(item), Instant.now());

        assertThat(existing.getOriginalTitle()).isEqualTo(item.title());
        assertThat(existing.getTranslatedTitle()).isEqualTo("아스널, 대형 영입 완료");
        assertThat(existing.getAutoTranslationAttempted()).isTrue();
    }

    @Test
    void queuesAutomaticRetranslationWhenRecollectedTitleMeaningfullyChanges() {
        Team team = Team.builder().teamId(1L).name("Arsenal").build();
        NewsArticle existing = NewsArticle.builder()
                .id(7L)
                .urlHash("hash")
                .originalUrl("https://bbc.com/sport/football/articles/shared")
                .originalTitle("Arsenal agree deal in principle")
                .translatedTitle("아스널, 원칙적 합의")
                .autoTranslationAttempted(true)
                .publisherName("BBC Sport")
                .publisherDomain("bbc.com")
                .build();
        var item = new SerpApiNewsClient.SearchArticle(
                "Arsenal complete the signing",
                existing.getOriginalUrl(),
                "BBC Sport",
                Instant.parse("2026-07-14T01:00:00Z")
        );
        when(teamRepository.findByTeamId(1L)).thenReturn(Optional.of(team));
        when(articleRepository.findByUrlHash(any())).thenReturn(Optional.of(existing));
        when(articleRepository.save(existing)).thenReturn(existing);
        when(serpApiNewsClient.publisherDomain(any())).thenReturn("bbc.com");
        when(relationRepository.findByTeamAndArticle(any(), any())).thenReturn(Optional.empty());

        service().saveTeamArticles(1L, List.of(item), Instant.now());

        assertThat(existing.getOriginalTitle()).isEqualTo(item.title());
        assertThat(existing.getTranslatedTitle()).isNull();
        assertThat(existing.getAutoTranslationAttempted()).isFalse();
    }

    @Test
    void storesArticlesInSerpApiResultOrder() {
        Team team = Team.builder().teamId(1L).name("Arsenal").build();
        var first = new SerpApiNewsClient.SearchArticle(
                "First result",
                "https://bbc.com/sport/football/articles/first",
                "BBC Sport",
                Instant.parse("2026-07-13T01:00:00Z")
        );
        var second = new SerpApiNewsClient.SearchArticle(
                "Second result",
                "https://bbc.com/sport/football/articles/second",
                "BBC Sport",
                Instant.parse("2026-07-14T01:00:00Z")
        );
        when(teamRepository.findByTeamId(1L)).thenReturn(Optional.of(team));
        when(articleRepository.findByUrlHash(any())).thenReturn(Optional.empty());
        when(articleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(serpApiNewsClient.publisherDomain(any())).thenReturn("bbc.com");
        when(relationRepository.findByTeamAndArticle(any(), any())).thenReturn(Optional.empty());

        NewsPersistenceService service = new NewsPersistenceService(
                teamRepository, articleRepository, relationRepository, collectionStateRepository, serpApiNewsClient);
        service.saveTeamArticles(1L, List.of(first, second), Instant.now());

        ArgumentCaptor<TeamNewsArticle> captor = ArgumentCaptor.forClass(TeamNewsArticle.class);
        verify(relationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TeamNewsArticle::getResultPosition)
                .containsExactly(0, 1);
        ArgumentCaptor<NewsArticle> articleCaptor = ArgumentCaptor.forClass(NewsArticle.class);
        verify(articleRepository, times(2)).save(articleCaptor.capture());
        assertThat(articleCaptor.getAllValues())
                .extracting(NewsArticle::getAutoTranslationAttempted)
                .containsOnly(false);
    }

    @Test
    void recordsCollectionTimeEvenWhenNoArticlesAreReturned() {
        Team team = Team.builder().id(99L).teamId(1L).name("Arsenal").build();
        Instant collectedAt = Instant.parse("2026-07-16T01:00:00Z");
        when(teamRepository.findByTeamId(1L)).thenReturn(Optional.of(team));

        NewsPersistenceService service = new NewsPersistenceService(
                teamRepository, articleRepository, relationRepository, collectionStateRepository, serpApiNewsClient);
        service.saveTeamArticles(1L, List.of(), collectedAt);

        ArgumentCaptor<TeamNewsCollectionState> captor = ArgumentCaptor.forClass(TeamNewsCollectionState.class);
        verify(collectionStateRepository).save(captor.capture());
        assertThat(captor.getValue().getLastCollectedAt()).isEqualTo(collectedAt);
        assertThat(captor.getValue().getTeam()).isSameAs(team);
        verify(collectionStateRepository).findByTeamTeamId(1L);
    }

    private NewsPersistenceService service() {
        return new NewsPersistenceService(
                teamRepository, articleRepository, relationRepository, collectionStateRepository, serpApiNewsClient);
    }
}
