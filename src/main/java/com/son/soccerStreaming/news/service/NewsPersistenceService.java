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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsPersistenceService {

    private final TeamRepository teamRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final TeamNewsArticleRepository teamNewsArticleRepository;
    private final TeamNewsCollectionStateRepository collectionStateRepository;
    private final SerpApiNewsClient serpApiNewsClient;

    @Transactional
    public int saveTeamArticles(Long teamId, List<SerpApiNewsClient.SearchArticle> articles, Instant seenAt) {
        Team team = teamRepository.findByTeamId(teamId).orElseThrow();
        int savedCount = 0;
        for (int resultPosition = 0; resultPosition < articles.size(); resultPosition++) {
            int currentPosition = resultPosition;
            SerpApiNewsClient.SearchArticle item = articles.get(resultPosition);
            String normalizedUrl = NewsUrlNormalizer.normalize(item.url());
            String urlHash = NewsUrlNormalizer.sha256(normalizedUrl);
            NewsArticle article = newsArticleRepository.findByUrlHash(urlHash)
                    .orElseGet(() -> NewsArticle.builder()
                            .urlHash(urlHash)
                            .originalUrl(normalizedUrl)
                            .originalTitle(item.title())
                            .autoTranslationAttempted(false)
                            .publisherName(item.publisherName())
                            .publisherDomain(serpApiNewsClient.publisherDomain(normalizedUrl))
                            .publishedAt(item.publishedAt())
                            .firstSeenAt(seenAt)
                            .lastSeenAt(seenAt)
                            .build());
            article.updateMetadata(
                    normalizedUrl,
                    item.title(),
                    item.publisherName(),
                    serpApiNewsClient.publisherDomain(normalizedUrl),
                    item.publishedAt(),
                    seenAt
            );
            NewsArticle savedArticle = newsArticleRepository.save(article);

            TeamNewsArticle relation = teamNewsArticleRepository.findByTeamAndArticle(team, savedArticle)
                    .orElseGet(() -> TeamNewsArticle.builder()
                            .team(team)
                            .article(savedArticle)
                            .firstSeenAt(seenAt)
                            .lastSeenAt(seenAt)
                            .resultPosition(currentPosition)
                            .build());
            relation.markSeen(seenAt, currentPosition);
            teamNewsArticleRepository.save(relation);
            savedCount++;
        }
        TeamNewsCollectionState collectionState = collectionStateRepository.findById(teamId)
                .orElseGet(() -> TeamNewsCollectionState.builder()
                        .team(team)
                        .lastCollectedAt(seenAt)
                        .build());
        collectionState.markCollected(seenAt);
        collectionStateRepository.save(collectionState);
        return savedCount;
    }
}
