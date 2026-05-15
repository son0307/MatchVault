package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
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

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixturePlayerStatSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final TeamRepository teamRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;

    @Transactional
    public int syncPlayerStats(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<ApiFootballLiveDto.FixturePlayersResponse> teamStats = apiFootballClient.getPlayerStats(fixtureId);
        return syncPlayerStats(fixture, teamStats);
    }

    @Transactional
    public int syncPlayerStats(Long fixtureId, List<ApiFootballLiveDto.FixturePlayersResponse> teamStats) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        return syncPlayerStats(fixture, teamStats);
    }

    @Transactional
    public int syncPlayerStats(Fixture fixture, List<ApiFootballLiveDto.FixturePlayersResponse> teamStats) {
        if (teamStats == null || teamStats.isEmpty()) {
            return 0;
        }

        int syncedCount = 0;

        for (ApiFootballLiveDto.FixturePlayersResponse teamStat : teamStats) {
            Optional<Team> team = findTeam(teamStat.getTeam());
            if (team.isEmpty() || teamStat.getPlayers() == null) {
                continue;
            }

            for (ApiFootballLiveDto.PlayerStatResponse playerStat : teamStat.getPlayers()) {
                if (upsertPlayerStat(fixture, team.get(), playerStat)) {
                    syncedCount++;
                }
            }
        }

        return syncedCount;
    }

    @Transactional
    public int syncPlayerStats(List<Fixture> fixtures) {
        int syncedCount = 0;
        for (Fixture fixture : fixtures) {
            try {
                syncedCount += syncPlayerStats(fixture.getFixtureId());
            } catch (Exception e) {
                log.error("API-Football fixture player stat sync failed. fixtureId={}", fixture.getFixtureId(), e);
            }
        }
        return syncedCount;
    }

    private boolean upsertPlayerStat(Fixture fixture, Team team, ApiFootballLiveDto.PlayerStatResponse playerStat) {
        Optional<Player> player = findPlayer(playerStat.getPlayer(), team);
        if (player.isEmpty()) {
            log.warn("Skip fixture player stat because player does not exist. fixtureId={}, playerId={}",
                    fixture.getFixtureId(), playerStat.getPlayer() != null ? playerStat.getPlayer().getId() : null);
            return false;
        }

        ApiFootballLiveDto.PlayerStatistics stat = firstStat(playerStat);
        if (stat == null) {
            return false;
        }

        PlayerFixtureStat entity = playerFixtureStatRepository
                .findByFixtureFixtureIdAndPlayerPlayerId(fixture.getFixtureId(), player.get().getPlayerId())
                .orElseGet(() -> playerFixtureStatRepository.save(PlayerFixtureStat.builder()
                        .fixture(fixture)
                        .team(team)
                        .player(player.get())
                        .build()));

        updatePlayerStat(entity, stat);
        return true;
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
        Integer passesTotal = passes != null ? passes.getTotal() : null;
        Integer passesAccurate = passes != null ? parseInteger(passes.getAccuracy()) : null;

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
                passesTotal,
                passes != null ? passes.getKey() : null,
                passesAccurate,
                passAccuracyOf(passesAccurate, passesTotal),
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

    private Integer passAccuracyOf(Integer passesAccurate, Integer passesTotal) {
        if (passesAccurate == null || passesTotal == null || passesTotal <= 0) {
            return null;
        }
        return (int) Math.round((passesAccurate * 100.0) / passesTotal);
    }
}
