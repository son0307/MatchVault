package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.config.NewsProperties;
import com.son.soccerStreaming.news.repository.NewsArticleRepository;
import com.son.soccerStreaming.news.repository.TeamNewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class NewsCleanupService {

    private final TeamNewsArticleRepository teamNewsArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsProperties properties;

    @Transactional
    public CleanupResult cleanupExpired() {
        Instant cutoff = Instant.now().minus(properties.getSync().getRetentionDays(), ChronoUnit.DAYS);
        int deletedRelations = teamNewsArticleRepository.deleteByLastSeenAtBefore(cutoff);
        teamNewsArticleRepository.flush();
        int deletedArticles = newsArticleRepository.deleteOrphans();
        return new CleanupResult(deletedRelations, deletedArticles);
    }

    public record CleanupResult(int deletedRelations, int deletedArticles) {
    }
}
