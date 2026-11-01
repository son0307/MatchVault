package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Keeps one published generation per season for the lifetime of an HTTP request. */
@Component
@RequiredArgsConstructor
public class FixtureDetailGenerationResolver {

    private static final Object NO_GENERATION = new Object();
    private final FixtureDetailGenerationStore generationStore;

    @Value("${fixture-detail.generation-reads.enabled:false}")
    private boolean enabled;

    public boolean enabled() {
        return enabled;
    }

    public Long activeForSeason(int season) {
        if (!enabled) {
            return null;
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null) {
            return generationStore.activeGenerationId(season);
        }
        String key = "fixture-detail-generation:" + season;
        Object pinned = request.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
        if (pinned != null) {
            return pinned == NO_GENERATION ? null : (Long) pinned;
        }
        Long generationId = generationStore.activeGenerationId(season);
        request.setAttribute(key, generationId == null ? NO_GENERATION : generationId,
                RequestAttributes.SCOPE_REQUEST);
        return generationId;
    }
}
