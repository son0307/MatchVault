package com.son.soccerStreaming.player.event;

public record PlayerSeasonStatRebuildRequested(
        Integer leagueId,
        Long fixtureId,
        Integer season
) {
}
