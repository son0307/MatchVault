package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminTeamNewsRefreshService {

    private final NewsCollectionService collectionService;
    private final NewsTitleTranslationService translationService;
    private final Set<Long> refreshingTeamIds = ConcurrentHashMap.newKeySet();

    public RefreshResult refresh(Long teamId) {
        if (!refreshingTeamIds.add(teamId)) {
            throw new CustomException(ErrorCode.ADMIN_SYNC_TOO_FREQUENT);
        }
        try {
            int collectedArticles = collectionService.collectTeam(teamId);
            NewsTitleTranslationService.TranslationRunResult translation =
                    translationService.translatePendingForTeam(teamId);
            return new RefreshResult(
                    collectedArticles,
                    translation.candidates(),
                    translation.translated(),
                    translation.failed()
            );
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // Do not propagate a RestClient exception because the SerpApi URL contains the API key.
            throw new CustomException(ErrorCode.NEWS_REFRESH_FAILED);
        } finally {
            refreshingTeamIds.remove(teamId);
        }
    }

    public record RefreshResult(
            int collectedArticles,
            int translationCandidates,
            int translatedArticles,
            int failedTranslations
    ) {
    }
}
