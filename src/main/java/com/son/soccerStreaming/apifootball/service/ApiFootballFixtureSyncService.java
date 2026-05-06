package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final TeamRepository teamRepository;
    private final ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;

    @Transactional
    public int syncSeasonFixtures(Integer league, Integer season) {
        return upsertFixtures(apiFootballClient.getFixtures(league, season), false);
    }

    @Transactional
    public int syncLiveFixtures(Integer league, Integer season) {
        List<ApiFootballLiveDto.FixtureResponse> liveFixtures = apiFootballClient.getLiveFixtures(league).stream()
                .filter(response -> matchesSeason(response, season))
                .toList();

        return upsertFixtures(liveFixtures, true);
    }

    private int upsertFixtures(List<ApiFootballLiveDto.FixtureResponse> responses, boolean applyLiveStandingImpact) {
        int syncedCount = 0;

        for (ApiFootballLiveDto.FixtureResponse response : responses) {
            Optional<Fixture> fixture = upsertFixture(response);
            if (fixture.isEmpty()) {
                continue;
            }

            if (applyLiveStandingImpact) {
                apiFootballStandingLocalUpdateService.applyFixtureState(fixture.get());
            }
            syncedCount++;
        }

        log.info("API-Football fixture sync completed. live={}, count={}", applyLiveStandingImpact, syncedCount);
        return syncedCount;
    }

    private Optional<Fixture> upsertFixture(ApiFootballLiveDto.FixtureResponse response) {
        ApiFootballLiveDto.FixtureInfo fixtureInfo = response.getFixture();
        ApiFootballLiveDto.Teams teams = response.getTeams();

        if (fixtureInfo == null || fixtureInfo.getId() == null || teams == null
                || teams.getHome() == null || teams.getHome().getId() == null
                || teams.getAway() == null || teams.getAway().getId() == null) {
            return Optional.empty();
        }

        Optional<Team> homeTeam = teamRepository.findByTeamId(teams.getHome().getId());
        Optional<Team> awayTeam = teamRepository.findByTeamId(teams.getAway().getId());
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
            log.warn("Skip fixture sync because team does not exist. fixtureId={}, homeTeamId={}, awayTeamId={}",
                    fixtureInfo.getId(), teams.getHome().getId(), teams.getAway().getId());
            return Optional.empty();
        }

        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureInfo.getId())
                .orElseGet(() -> Fixture.builder()
                        .fixtureId(fixtureInfo.getId())
                        .homeTeam(homeTeam.get())
                        .awayTeam(awayTeam.get())
                        .fixtureDate(parseFixtureDate(fixtureInfo.getDate(), LocalDateTime.now()))
                        .build());

        updateFixture(fixture, response);
        return Optional.of(fixtureRecordRepository.save(fixture));
    }

    private boolean matchesSeason(ApiFootballLiveDto.FixtureResponse response, Integer season) {
        ApiFootballLiveDto.LeagueInfo leagueInfo = response.getLeague();
        if (leagueInfo == null) {
            return false;
        }

        return leagueInfo.getSeason() != null && leagueInfo.getSeason().equals(season);
    }

    private void updateFixture(Fixture fixture, ApiFootballLiveDto.FixtureResponse response) {
        ApiFootballLiveDto.FixtureInfo fixtureInfo = response.getFixture();
        ApiFootballLiveDto.LeagueInfo league = response.getLeague();
        ApiFootballLiveDto.Status status = fixtureInfo != null ? fixtureInfo.getStatus() : null;
        ApiFootballLiveDto.Goals goals = response.getGoals();
        ApiFootballLiveDto.Teams teams = response.getTeams();
        ApiFootballLiveDto.Score score = response.getScore();

        String statusShort = status != null ? status.getShortStatus() : fixture.getStatusShort();
        String statusLong = status != null ? status.getLongStatus() : fixture.getStatusLong();

        ApiFootballLiveDto.Periods periods = fixtureInfo != null ? fixtureInfo.getPeriods() : null;
        ApiFootballLiveDto.Venue venue = fixtureInfo != null ? fixtureInfo.getVenue() : null;
        fixture.updateFixtureMetadata(
                parseFixtureDate(fixtureInfo != null ? fixtureInfo.getDate() : null, fixture.getFixtureDate()),
                fixtureInfo != null ? fixtureInfo.getReferee() : fixture.getReferee(),
                fixtureInfo != null ? fixtureInfo.getTimezone() : fixture.getTimezone(),
                fixtureInfo != null ? fixtureInfo.getTimestamp() : fixture.getTimestamp(),
                periods != null ? periods.getFirst() : fixture.getFirstPeriod(),
                periods != null ? periods.getSecond() : fixture.getSecondPeriod(),
                venue != null ? venue.getId() : fixture.getVenueId(),
                venue != null ? venue.getName() : fixture.getVenueName(),
                venue != null ? venue.getCity() : fixture.getVenueCity()
        );

        fixture.updateFixtureState(
                statusShort,
                statusLong,
                fixtureStatusOf(statusShort),
                status != null ? status.getElapsed() : fixture.getElapsed(),
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

        if (league != null && league.getRound() != null) {
            fixture.updateRound(league.getRound());
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
}
