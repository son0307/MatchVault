package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.client.OpenAiTitleTranslationClient;
import com.son.soccerStreaming.news.config.NewsProperties;
import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsTitleTranslationService {

    private final NewsArticleRepository newsArticleRepository;
    private final OpenAiTitleTranslationClient translationClient;
    private final NewsTranslationPersistenceService persistenceService;
    private final NewsProperties properties;

    public TranslationRunResult translatePending() {
        List<NewsArticle> candidates = newsArticleRepository.findTranslationCandidates();
        return translateCandidates(candidates);
    }

    public TranslationRunResult translatePendingForTeam(Long teamId) {
        List<NewsArticle> candidates = newsArticleRepository.findTranslationCandidatesByTeamId(teamId);
        return translateCandidates(candidates);
    }

    private TranslationRunResult translateCandidates(List<NewsArticle> candidates) {
        int translated = 0;
        int failed = 0;
        int batchSize = Math.max(1, properties.getTranslation().getBatchSize());

        for (int start = 0; start < candidates.size(); start += batchSize) {
            List<NewsArticle> batch = new ArrayList<>(candidates.subList(
                    start,
                    Math.min(start + batchSize, candidates.size())
            ));
            List<Long> requestedIds = batch.stream().map(NewsArticle::getId).toList();
            persistenceService.markAutoTranslationAttempted(requestedIds);
            try {
                Map<Long, String> translations = translationClient.translate(batch.stream()
                        .map(article -> new OpenAiTitleTranslationClient.TranslationInput(
                                article.getId(),
                                article.getOriginalTitle()
                        ))
                        .toList());
                persistenceService.applyResults(requestedIds, translations);
                translated += (int) requestedIds.stream()
                        .filter(id -> translations.get(id) != null && !translations.get(id).isBlank())
                        .count();
                failed += requestedIds.size() - (int) requestedIds.stream()
                        .filter(id -> translations.get(id) != null && !translations.get(id).isBlank())
                        .count();
            } catch (Exception e) {
                failed += batch.size();
                log.warn("News title translation batch failed. batchSize={}", batch.size(), e);
            }
        }
        return new TranslationRunResult(candidates.size(), translated, failed);
    }

    public record TranslationRunResult(int candidates, int translated, int failed) {
    }
}
