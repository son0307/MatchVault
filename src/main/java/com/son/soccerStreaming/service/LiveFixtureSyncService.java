package com.son.soccerStreaming.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureEventSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureStatSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixturePlayerStatSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import com.son.soccerStreaming.dto.FixtureEventDto;
import com.son.soccerStreaming.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.dto.LiveFixtureSnapshotDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveFixtureSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final FixtureRedisService fixtureRedisService;
    private final LiveFixtureSnapshotService liveFixtureSnapshotService;
    private final ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    private final ApiFootballFixtureEventSyncService apiFootballFixtureEventSyncService;
    private final ApiFootballFixtureStatSyncService apiFootballFixtureStatSyncService;
    private final ApiFootballFixturePlayerStatSyncService apiFootballFixturePlayerStatSyncService;
    private final FixtureEventService fixtureEventService;
    private final FixturePlayerStatService fixturePlayerStatService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void syncFixture(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<ApiFootballLiveDto.FixtureResponse> fixtureResponses = apiFootballClient.getFixture(fixtureId);
        fixtureResponses.stream()
                .findFirst()
                .ifPresent(response -> updateFixtureState(fixture, response));

        FixtureEventDto latestEvent = apiFootballFixtureEventSyncService.syncEvents(fixtureId);

        apiFootballFixtureStatSyncService.syncFixtureStats(fixtureId);

        apiFootballFixturePlayerStatSyncService.syncPlayerStats(fixtureId);

        apiFootballStandingLocalUpdateService.applyFixtureState(fixture);

        LiveFixtureSnapshotDto snapshot = liveFixtureSnapshotService.rebuildAndCacheSnapshot(fixtureId, latestEvent);
        FixturePlayerStatResponseDto playerStats = fixturePlayerStatService.getFixturePlayerStats(fixtureId);
        fixtureRedisService.savePlayerStats(playerStats);

        broadcast(fixtureId, "LIVE_SNAPSHOT", snapshot);
        broadcast(fixtureId, "FIXTURE_EVENTS", fixtureEventService.getFixtureEvents(fixtureId));
        broadcast(fixtureId, "PLAYER_STATS", playerStats);
    }

    private void updateFixtureState(Fixture fixture, ApiFootballLiveDto.FixtureResponse response) {
        ApiFootballLiveDto.FixtureInfo fixtureInfo = response.getFixture();
        ApiFootballLiveDto.Status status = fixtureInfo != null ? fixtureInfo.getStatus() : null;
        ApiFootballLiveDto.Goals goals = response.getGoals();
        ApiFootballLiveDto.Teams teams = response.getTeams();
        ApiFootballLiveDto.Score score = response.getScore();

        String statusShort = status != null ? status.getShortStatus() : fixture.getStatusShort();
        String statusLong = status != null ? status.getLongStatus() : fixture.getStatusLong();
        Integer elapsed = status != null ? status.getElapsed() : fixture.getElapsed();

        if (fixtureInfo != null) {
            ApiFootballLiveDto.Periods periods = fixtureInfo.getPeriods();
            ApiFootballLiveDto.Venue venue = fixtureInfo.getVenue();
            fixture.updateFixtureMetadata(
                    parseFixtureDate(fixtureInfo.getDate(), fixture.getFixtureDate()),
                    fixtureInfo.getReferee(),
                    fixtureInfo.getTimezone(),
                    fixtureInfo.getTimestamp(),
                    periods != null ? periods.getFirst() : null,
                    periods != null ? periods.getSecond() : null,
                    venue != null ? venue.getId() : fixture.getVenueId(),
                    venue != null ? venue.getName() : fixture.getVenueName(),
                    venue != null ? venue.getCity() : fixture.getVenueCity()
            );
        }

        fixture.updateFixtureState(
                statusShort,
                statusLong,
                fixtureStatusOf(statusShort),
                elapsed,
                goals != null ? goals.getHome() : fixture.getHomeScore(),
                goals != null ? goals.getAway() : fixture.getAwayScore()
        );

        fixture.updateTeamResult(
                teams != null && teams.getHome() != null ? teams.getHome().getWinner() : fixture.getHomeWinner(),
                teams != null && teams.getAway() != null ? teams.getAway().getWinner() : fixture.getAwayWinner()
        );

        if (score != null) {
            fixture.updateScoreBreakdown(
                    homeScoreOf(score.getHalftime()),
                    awayScoreOf(score.getHalftime()),
                    homeScoreOf(score.getFulltime()),
                    awayScoreOf(score.getFulltime()),
                    homeScoreOf(score.getExtratime()),
                    awayScoreOf(score.getExtratime()),
                    homeScoreOf(score.getPenalty()),
                    awayScoreOf(score.getPenalty())
            );
        }
    }

    private String fixtureStatusOf(String statusShort) {
        if (statusShort == null || "NS".equals(statusShort) || "TBD".equals(statusShort)) {
            return "SCHEDULED";
        }
        if ("FT".equals(statusShort) || "AET".equals(statusShort) || "PEN".equals(statusShort)) {
            return "FINISHED";
        }
        return "LIVE";
    }

    private LocalDateTime parseFixtureDate(String date, LocalDateTime fallback) {
        if (date == null || date.isBlank()) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(date).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse API-Football fixture date. date={}", date);
            return fallback;
        }
    }

    private Integer homeScoreOf(ApiFootballLiveDto.ScoreDetail score) {
        return score != null ? score.getHome() : null;
    }

    private Integer awayScoreOf(ApiFootballLiveDto.ScoreDetail score) {
        return score != null ? score.getAway() : null;
    }

    private void broadcast(Long fixtureId, String eventName, Object payload) {
        try {
            sseService.broadcastToFixture(String.valueOf(fixtureId), eventName, objectMapper.writeValueAsString(payload));
        } catch (JacksonException e) {
            log.error("Failed to serialize SSE payload. fixtureId={}, eventName={}", fixtureId, eventName, e);
        }
    }
}
