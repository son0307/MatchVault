package com.son.soccerStreaming.news.controller;

import com.son.soccerStreaming.news.service.AdminTeamNewsRefreshService;
import com.son.soccerStreaming.news.service.AdminNewsTranslationService;
import lombok.RequiredArgsConstructor;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/teams/{teamId}/news")
@RequiredArgsConstructor
public class AdminTeamNewsController {

    private final AdminTeamNewsRefreshService refreshService;
    private final AdminNewsTranslationService translationService;

    @PostMapping("/refresh")
    public AdminTeamNewsRefreshService.RefreshResult refresh(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId
    ) {
        return refreshService.refresh(userDetails.getId(), teamId);
    }

    @PostMapping("/{articleId}/translate")
    public AdminNewsTranslationService.TranslationResult translate(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @PathVariable Long articleId
    ) {
        return translationService.translate(userDetails.getId(), teamId, articleId);
    }
}
