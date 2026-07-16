package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.config.NewsProperties;
import com.son.soccerStreaming.news.repository.NewsArticleRepository;
import com.son.soccerStreaming.news.repository.TeamNewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsCleanupServiceTest {

    @Mock TeamNewsArticleRepository relationRepository;
    @Mock NewsArticleRepository articleRepository;

    @Test
    void deletesExpiredRelationsBeforeDeletingOnlyOrphanArticles() {
        NewsProperties properties = new NewsProperties();
        properties.getSync().setRetentionDays(90);
        when(relationRepository.deleteByLastSeenAtBefore(argThat(this::isAboutNinetyDaysAgo))).thenReturn(3);
        when(articleRepository.deleteOrphans()).thenReturn(1);

        var result = new NewsCleanupService(relationRepository, articleRepository, properties).cleanupExpired();

        assertThat(result).isEqualTo(new NewsCleanupService.CleanupResult(3, 1));
        InOrder order = inOrder(relationRepository, articleRepository);
        order.verify(relationRepository).deleteByLastSeenAtBefore(argThat(this::isAboutNinetyDaysAgo));
        order.verify(relationRepository).flush();
        order.verify(articleRepository).deleteOrphans();
    }

    private boolean isAboutNinetyDaysAgo(Instant value) {
        long differenceSeconds = Math.abs(value.getEpochSecond() - Instant.now().minusSeconds(90L * 24 * 60 * 60).getEpochSecond());
        return differenceSeconds < 5;
    }
}
