package com.son.soccerStreaming.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureEventSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import com.son.soccerStreaming.dto.FixtureEventDto;
import com.son.soccerStreaming.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.dto.LiveFixtureSnapshotDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.PlayerFixtureStat;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import com.son.soccerStreaming.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.repository.TeamRepository;
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
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final TeamRepository teamRepository;
    private final FixtureRedisService fixtureRedisService;
    private final LiveFixtureSnapshotService liveFixtureSnapshotService;
    private final ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    private final ApiFootballFixtureEventSyncService apiFootballFixtureEventSyncService;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
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

        List<ApiFootballLiveDto.FixturePlayersResponse> playerStatResponses = apiFootballClient.getPlayerStats(fixtureId);
        upsertPlayerStats(fixture, playerStatResponses);

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

    private void upsertPlayerStats(Fixture fixture, List<ApiFootballLiveDto.FixturePlayersResponse> teamStats) {
        for (ApiFootballLiveDto.FixturePlayersResponse teamStat : teamStats) {
            Optional<Team> team = findTeam(teamStat.getTeam());
            if (team.isEmpty() || teamStat.getPlayers() == null) {
                continue;
            }

            for (ApiFootballLiveDto.PlayerStatResponse playerStat : teamStat.getPlayers()) {
                Optional<Player> player = findPlayer(playerStat.getPlayer(), team.get());
                if (player.isEmpty()) {
                    log.warn("Skip live player stat because player does not exist. fixtureId={}, playerId={}",
                            fixture.getFixtureId(), playerStat.getPlayer() != null ? playerStat.getPlayer().getId() : null);
                    continue;
                }

                ApiFootballLiveDto.PlayerStatistics stat = firstStat(playerStat);
                if (stat == null) {
                    continue;
                }

                PlayerFixtureStat entity = playerFixtureStatRepository
                        .findByFixtureFixtureIdAndPlayerPlayerId(fixture.getFixtureId(), player.get().getPlayerId())
                        .orElseGet(() -> playerFixtureStatRepository.save(PlayerFixtureStat.builder()
                                .fixture(fixture)
                                .team(team.get())
                                .player(player.get())
                                .build()));

                updatePlayerStat(entity, stat);
            }
        }
    }

    private void updatePlayerStat(PlayerFixtureStat entity, ApiFootballLiveDto.PlayerStatistics stat) {
        ApiFootballLiveDto.Games games = stat.getGames();
        ApiFootballLiveDto.GoalsStat goals = stat.getGoals();
        ApiFootballLiveDto.Shots shots = stat.getShots();
        ApiFootballLiveDto.Passes passes = stat.getPasses();
        ApiFootballLiveDto.Tackles tackles = stat.getTackles();
        ApiFootballLiveDto.Duels duels = stat.getDuels();
        ApiFootballLiveDto.Dribbles dribbles = stat.getDribbles();
        ApiFootballLiveDto.Fouls fouls = stat.getFouls();
        ApiFootballLiveDto.Cards cards = stat.getCards();
        ApiFootballLiveDto.Penalty penalty = stat.getPenalty();

        entity.updateLiveStat(
                games != null ? games.getMinutes() : null,
                games != null ? parseDouble(games.getRating()) : null,
                games != null ? games.getCaptain() : null,
                games != null ? games.getSubstitute() : null,
                goals != null ? goals.getTotal() : null,
                goals != null ? goals.getAssists() : null,
                goals != null ? goals.getConceded() : null,
                goals != null ? goals.getSaves() : null,
                shots != null ? shots.getTotal() : null,
                shots != null ? shots.getOn() : null,
                passes != null ? passes.getTotal() : null,
                passes != null ? passes.getKey() : null,
                passes != null ? parseInteger(passes.getAccuracy()) : null,
                tackles != null ? tackles.getTotal() : null,
                tackles != null ? tackles.getBlocks() : null,
                tackles != null ? tackles.getInterceptions() : null,
                duels != null ? duels.getTotal() : null,
                duels != null ? duels.getWon() : null,
                dribbles != null ? dribbles.getAttempts() : null,
                dribbles != null ? dribbles.getSuccess() : null,
                dribbles != null ? dribbles.getPast() : null,
                fouls != null ? fouls.getDrawn() : null,
                fouls != null ? fouls.getCommitted() : null,
                cards != null ? cards.getYellow() : null,
                cards != null ? cards.getRed() : null,
                stat.getOffsides(),
                penalty != null ? penalty.getWon() : null,
                penalty != null ? penalty.getCommited() : null,
                penalty != null ? penalty.getScored() : null,
                penalty != null ? penalty.getMissed() : null,
                penalty != null ? penalty.getSaved() : null
        );
    }

    private Optional<Team> findTeam(ApiFootballLiveDto.TeamInfo team) {
        if (team == null || team.getId() == null) {
            return Optional.empty();
        }
        return teamRepository.findByTeamId(team.getId());
    }

    private Optional<Player> findPlayer(ApiFootballLiveDto.PlayerInfo player) {
        return findPlayer(player, null);
    }

    private Optional<Player> findPlayer(ApiFootballLiveDto.PlayerInfo player, Team team) {
        if (player == null || player.getId() == null) {
            return Optional.empty();
        }
        return apiFootballPlayerSyncService.findOrFetchPlayer(
                player.getId(),
                player.getName(),
                team,
                null,
                null,
                player.getPhoto()
        );
    }

    private ApiFootballLiveDto.PlayerStatistics firstStat(ApiFootballLiveDto.PlayerStatResponse playerStat) {
        if (playerStat.getStatistics() == null || playerStat.getStatistics().isEmpty()) {
            return null;
        }
        return playerStat.getStatistics().get(0);
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

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.parseDouble(value);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        return digits.isBlank() ? null : Integer.parseInt(digits);
    }

    private void broadcast(Long fixtureId, String eventName, Object payload) {
        try {
            sseService.broadcastToFixture(String.valueOf(fixtureId), eventName, objectMapper.writeValueAsString(payload));
        } catch (JacksonException e) {
            log.error("Failed to serialize SSE payload. fixtureId={}, eventName={}", fixtureId, eventName, e);
        }
    }
}
