package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsTranslationPersistenceService {

    private final NewsArticleRepository newsArticleRepository;

    @Transactional
    public void markAutoTranslationAttempted(Collection<Long> requestedIds) {
        newsArticleRepository.findAllById(requestedIds)
                .forEach(NewsArticle::markAutoTranslationAttempted);
    }

    @Transactional
    public void applyResults(Collection<Long> requestedIds, Map<Long, String> translations) {
        Map<Long, NewsArticle> articles = newsArticleRepository.findAllById(requestedIds).stream()
                .collect(java.util.stream.Collectors.toMap(NewsArticle::getId, article -> article));
        for (Long requestedId : requestedIds) {
            NewsArticle article = articles.get(requestedId);
            if (article == null) {
                continue;
            }
            String translatedTitle = translations.get(requestedId);
            if (StringUtils.hasText(translatedTitle) && translatedTitle.length() <= 1000) {
                article.markTranslated(translatedTitle.trim());
            }
        }
    }
}
