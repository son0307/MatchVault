package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLineupDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureLineupSyncService {

    private static final String UNKNOWN_POSITION = "N/A";

    private final ApiFootballClient apiFootballClient;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final FixtureLineupRepository fixtureLineupRepository;
    private final TeamRepository teamRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;

    @Transactional
    public int syncLineups(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<ApiFootballLineupDto.LineupResponse> lineups = apiFootballClient.getLineups(fixtureId);
        return syncLineups(fixture, lineups);
    }

    @Transactional
    public int syncLineups(Long fixtureId, List<ApiFootballLineupDto.LineupResponse> lineups) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        return syncLineups(fixture, lineups);
    }

    @Transactional
    public int syncLineups(Fixture fixture, List<ApiFootballLineupDto.LineupResponse> lineups) {
        if (lineups == null || lineups.isEmpty()) {
            return 0;
        }

        int syncedCount = 0;

        for (ApiFootballLineupDto.LineupResponse lineup : lineups) {
            Optional<Team> team = findTeam(lineup.getTeam());
            if (team.isEmpty()) {
                continue;
            }

            updateFixtureTactics(fixture, team.get(), lineup);
            syncedCount += upsertPlayers(fixture, team.get(), lineup.getStartXI(), true);
            syncedCount += upsertPlayers(fixture, team.get(), lineup.getSubstitutes(), false);
        }

        return syncedCount;
    }

    @Transactional
    public int syncLineups(List<Fixture> fixtures) {
        int syncedCount = 0;
        for (Fixture fixture : fixtures) {
            try {
                syncedCount += syncLineups(fixture.getFixtureId());
            } catch (Exception e) {
                log.error("API-Football fixture lineup sync failed. fixtureId={}", fixture.getFixtureId(), e);
            }
        }
        return syncedCount;
    }

    private int upsertPlayers(Fixture fixture, Team team, List<ApiFootballLineupDto.PlayerEntry> entries, boolean starter) {
        if (entries == null) {
            return 0;
        }

        int syncedCount = 0;
        for (ApiFootballLineupDto.PlayerEntry entry : entries) {
            ApiFootballLineupDto.PlayerInfo playerInfo = entry.getPlayer();
            if (playerInfo == null || playerInfo.getId() == null) {
                continue;
            }

            Optional<Player> player = apiFootballPlayerSyncService.findOrFetchPlayer(
                    playerInfo.getId(),
                    playerInfo.getName(),
                    team,
                    playerInfo.getNumber(),
                    playerInfo.getPos(),
                    null
            );
            if (player.isEmpty()) {
                log.warn("Skip lineup player because player does not exist. fixtureId={}, playerId={}",
                        fixture.getFixtureId(), playerInfo.getId());
                continue;
            }

            FixtureLineup lineup = fixtureLineupRepository
                    .findByFixtureFixtureIdAndTeamTeamIdAndPlayerPlayerId(
                            fixture.getFixtureId(),
                            team.getTeamId(),
                            player.get().getPlayerId()
                    )
                    .orElseGet(() -> FixtureLineup.builder()
                            .fixture(fixture)
                            .team(team)
                            .player(player.get())
                            .build());

            lineup.updateLineup(
                    valueOrZero(playerInfo.getNumber()),
                    valueOrUnknown(playerInfo.getPos()),
                    playerInfo.getGrid(),
                    starter
            );
            fixtureLineupRepository.save(lineup);
            apiFootballPlayerSyncService.updateLineupProfileIfLatest(
                    player.get(),
                    fixture,
                    playerInfo.getNumber(),
                    playerInfo.getPos()
            );
            apiFootballPlayerSyncService.updateSeasonBackNumberFromLineup(
                    player.get(),
                    team,
                    fixture,
                    playerInfo.getNumber()
            );
            syncedCount++;
        }

        return syncedCount;
    }

    private Optional<Team> findTeam(ApiFootballLineupDto.TeamInfo teamInfo) {
        if (teamInfo == null || teamInfo.getId() == null) {
            return Optional.empty();
        }
        return teamRepository.findByTeamId(teamInfo.getId());
    }

    private void updateFixtureTactics(Fixture fixture, Team team, ApiFootballLineupDto.LineupResponse lineup) {
        String coachName = lineup.getCoach() != null ? lineup.getCoach().getName() : null;
        if (fixture.getHomeTeam().getTeamId().equals(team.getTeamId())) {
            fixture.updateTactics(
                    lineup.getFormation(),
                    fixture.getAwayFormation(),
                    coachName,
                    fixture.getAwayCoachName()
            );
            updateHomeColors(fixture, lineup.getTeam() != null ? lineup.getTeam().getUniformColors() : null);
        }
        if (fixture.getAwayTeam().getTeamId().equals(team.getTeamId())) {
            fixture.updateTactics(
                    fixture.getHomeFormation(),
                    lineup.getFormation(),
                    fixture.getHomeCoachName(),
                    coachName
            );
            updateAwayColors(fixture, lineup.getTeam() != null ? lineup.getTeam().getUniformColors() : null);
        }
    }

    private void updateHomeColors(Fixture fixture, ApiFootballLineupDto.UniformColors uniformColors) {
        ApiFootballLineupDto.ColorInfo player = uniformColors != null ? uniformColors.getPlayer() : null;
        ApiFootballLineupDto.ColorInfo goalkeeper = uniformColors != null ? uniformColors.getGoalkeeper() : null;
        fixture.updateHomeLineupColors(
                primaryOf(player),
                numberOf(player),
                borderOf(player),
                primaryOf(goalkeeper),
                numberOf(goalkeeper),
                borderOf(goalkeeper)
        );
    }

    private void updateAwayColors(Fixture fixture, ApiFootballLineupDto.UniformColors uniformColors) {
        ApiFootballLineupDto.ColorInfo player = uniformColors != null ? uniformColors.getPlayer() : null;
        ApiFootballLineupDto.ColorInfo goalkeeper = uniformColors != null ? uniformColors.getGoalkeeper() : null;
        fixture.updateAwayLineupColors(
                primaryOf(player),
                numberOf(player),
                borderOf(player),
                primaryOf(goalkeeper),
                numberOf(goalkeeper),
                borderOf(goalkeeper)
        );
    }

    private String primaryOf(ApiFootballLineupDto.ColorInfo color) {
        return color != null ? color.getPrimary() : null;
    }

    private String numberOf(ApiFootballLineupDto.ColorInfo color) {
        return color != null ? color.getNumber() : null;
    }

    private String borderOf(ApiFootballLineupDto.ColorInfo color) {
        return color != null ? color.getBorder() : null;
    }

    private Integer valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN_POSITION : value;
    }
}
