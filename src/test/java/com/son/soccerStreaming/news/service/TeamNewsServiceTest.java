package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.entity.TeamNewsArticle;
import com.son.soccerStreaming.news.entity.TeamNewsCollectionState;
import com.son.soccerStreaming.news.repository.TeamNewsArticleRepository;
import com.son.soccerStreaming.news.repository.TeamNewsCollectionStateRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamNewsServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock TeamNewsArticleRepository repository;
    @Mock TeamNewsCollectionStateRepository collectionStateRepository;

    @Test
    void returnsNullableTranslationAndRequestsOnlyLatestTwenty() {
        Team team = Team.builder().teamId(42L).name("Arsenal").build();
        Instant publishedAt = Instant.parse("2026-07-14T01:00:00Z");
        NewsArticle article = NewsArticle.builder()
                .id(7L)
                .originalTitle("Original")
                .translatedTitle(null)
                .publisherName("BBC Sport")
                .originalUrl("https://bbc.com/sport/football/articles/7")
                .publishedAt(publishedAt)
                .build();
        TeamNewsArticle relation = TeamNewsArticle.builder().team(team).article(article).build();
        when(teamRepository.findByTeamId(42L)).thenReturn(Optional.of(team));
        when(repository.findLatestByTeamId(42L, PageRequest.of(0, 20))).thenReturn(List.of(relation));
        when(collectionStateRepository.findById(42L)).thenReturn(Optional.of(
                TeamNewsCollectionState.builder().team(team).lastCollectedAt(publishedAt).build()
        ));

        var result = new TeamNewsService(teamRepository, repository, collectionStateRepository).getTeamNews(42L);

        assertThat(result.articles()).hasSize(1);
        assertThat(result.articles().get(0).translatedTitle()).isNull();
        assertThat(result.articles().get(0).publishedAt()).isEqualTo(publishedAt);
        assertThat(result.lastCollectedAt()).isEqualTo(publishedAt);
        verify(repository).findLatestByTeamId(42L, PageRequest.of(0, 20));
    }

    @Test
    void rejectsUnknownTeamWithoutReadingNews() {
        when(teamRepository.findByTeamId(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new TeamNewsService(teamRepository, repository, collectionStateRepository).getTeamNews(404L))
                .isInstanceOf(CustomException.class);
        verify(teamRepository).findByTeamId(404L);
    }
}
