package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.news.client.OpenAiTitleTranslationClient;
import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.repository.TeamNewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNewsTranslationService {

    private final TeamNewsArticleRepository teamNewsArticleRepository;
    private final OpenAiTitleTranslationClient translationClient;
    private final NewsTranslationPersistenceService persistenceService;

    public TranslationResult translate(Long teamId, Long articleId) {
        NewsArticle article = teamNewsArticleRepository.findByTeamIdAndArticleId(teamId, articleId)
                .orElseThrow(() -> new CustomException(ErrorCode.NEWS_ARTICLE_NOT_FOUND))
                .getArticle();
        if (StringUtils.hasText(article.getTranslatedTitle())) {
            return new TranslationResult(articleId, article.getTranslatedTitle());
        }

        try {
            Map<Long, String> translations = translationClient.translate(List.of(
                    new OpenAiTitleTranslationClient.TranslationInput(articleId, article.getOriginalTitle())
            ));
            String translatedTitle = translations.get(articleId);
            if (!StringUtils.hasText(translatedTitle) || translatedTitle.length() > 1000) {
                throw new IllegalStateException("OpenAI did not return a valid translation for the requested article.");
            }
            String normalizedTitle = translatedTitle.trim();
            persistenceService.applyResults(List.of(articleId), Map.of(articleId, normalizedTitle));
            return new TranslationResult(articleId, normalizedTitle);
        } catch (Exception e) {
            log.warn("Manual news title translation failed. teamId={}, articleId={}, errorType={}",
                    teamId, articleId, e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.NEWS_TRANSLATION_FAILED);
        }
    }

    public record TranslationResult(Long articleId, String translatedTitle) {
    }
}
