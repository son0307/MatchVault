package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLineupDto;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureLineupSyncService {

    private static final String UNKNOWN_POSITION = "N/A";

    private final ApiFootballClient apiFootballClient;
    private final FixtureRepository fixtureRepository;
    private final FixtureLineupRepository fixtureLineupRepository;
    private final TeamRepository teamRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final AdminOverrideService adminOverrideService;
    private final OptimisticLockRetryExecutor optimisticLockRetryExecutor;
    private static final List<String> LINEUP_OVERRIDE_FIELDS = List.of(
            "jerseyNumber", "position", "grid", "starter"
    );
    private static final List<String> FIXTURE_TACTIC_OVERRIDE_FIELDS = List.of(
            "homeFormation", "awayFormation", "homeCoachName", "awayCoachName",
            "homePlayerColorPrimary", "homePlayerColorNumber", "homePlayerColorBorder",
            "homeGoalkeeperColorPrimary", "homeGoalkeeperColorNumber", "homeGoalkeeperColorBorder",
            "awayPlayerColorPrimary", "awayPlayerColorNumber", "awayPlayerColorBorder",
            "awayGoalkeeperColorPrimary", "awayGoalkeeperColorNumber", "awayGoalkeeperColorBorder"
    );

    public int syncLineups(Long fixtureId) {
        List<ApiFootballLineupDto.LineupResponse> lineups = apiFootballClient.getLineups(fixtureId);
        return persistLineupsWithRetry(fixtureId, lineups);
    }

    public int syncLineups(Long fixtureId, List<ApiFootballLineupDto.LineupResponse> lineups) {
        return persistLineupsWithRetry(fixtureId, lineups);
    }

    private int persistLineupsWithRetry(Long fixtureId, List<ApiFootballLineupDto.LineupResponse> lineups) {
        return optimisticLockRetryExecutor.execute(
                "fixture-lineups:fixture=%s".formatted(fixtureId),
                () -> {
                    Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                            .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
                    return syncLineups(fixture, lineups);
                }
        );
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

    /** Updates the fixture draft's tactics and returns detached lineup rows for a private generation. */
    public List<FixtureLineup> previewLineups(Fixture currentFixture, Fixture fixtureDraft,
                                               List<ApiFootballLineupDto.LineupResponse> lineups) {
        if (lineups == null || lineups.isEmpty()) {
            return List.of();
        }
        List<FixtureLineup> drafts = new ArrayList<>();
        for (ApiFootballLineupDto.LineupResponse response : lineups) {
            if (response == null) {
                throw new IllegalArgumentException("Fixture lineup response contains null");
            }
            Team team = findTeam(response.getTeam()).orElseThrow(() ->
                    new IllegalArgumentException("Fixture lineup team is missing"));
            updateFixtureTactics(fixtureDraft, team, response);
            previewPlayers(currentFixture, team, response.getStartXI(), true, drafts);
            previewPlayers(currentFixture, team, response.getSubstitutes(), false, drafts);
        }
        return drafts;
    }

    private void previewPlayers(Fixture fixture, Team team, List<ApiFootballLineupDto.PlayerEntry> entries,
                                boolean starter, List<FixtureLineup> drafts) {
        if (entries == null) {
            return;
        }
        for (ApiFootballLineupDto.PlayerEntry entry : entries) {
            ApiFootballLineupDto.PlayerInfo info = entry != null ? entry.getPlayer() : null;
            if (info == null || info.getId() == null) {
                throw new IllegalArgumentException("Fixture lineup player ID is missing");
            }
            if (info.getId() <= 0) {
                log.warn("Skip fixture lineup player with invalid ID. fixtureId={}, playerId={}",
                        fixture.getFixtureId(), info.getId());
                continue;
            }
            Player player = apiFootballPlayerSyncService.findOrFetchPlayer(
                    info.getId(), info.getName(), team, info.getNumber(), info.getPos(), null)
                    .orElseThrow(() -> new IllegalArgumentException("Fixture lineup player is missing: " + info.getId()));
            FixtureLineup current = fixtureLineupRepository
                    .findByFixtureFixtureIdAndTeamTeamIdAndPlayerPlayerId(
                            fixture.getFixtureId(), team.getTeamId(), player.getPlayerId())
                    .orElseGet(() -> FixtureLineup.builder()
                            .fixture(fixture).team(team).player(player)
                            .jerseyNumber(valueOrZero(info.getNumber()))
                            .position(valueOrUnknown(info.getPos()))
                            .grid(info.getGrid()).isStarter(starter).build());
            FixtureLineup draft = current.toBuilder().build();
            Set<String> overrides = current.getId() == null ? Set.of()
                    : adminOverrideService.overriddenFields(
                            AdminOverrideTargetType.FIXTURE_LINEUP, current.getId(), LINEUP_OVERRIDE_FIELDS);
            draft.updateLineup(
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "jerseyNumber", draft.getJerseyNumber(), valueOrZero(info.getNumber())),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "position", draft.getPosition(), valueOrUnknown(info.getPos())),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "grid", draft.getGrid(), info.getGrid()),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "starter", draft.isStarter(), starter)
            );
            drafts.add(draft);
        }
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
            ApiFootballLineupDto.PlayerInfo playerInfo = entry != null ? entry.getPlayer() : null;
            if (playerInfo == null || playerInfo.getId() == null || playerInfo.getId() <= 0) {
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
                            .jerseyNumber(valueOrZero(playerInfo.getNumber()))
                            .position(valueOrUnknown(playerInfo.getPos()))
                            .grid(playerInfo.getGrid())
                            .isStarter(starter)
                            .build());

            if (lineup.getId() == null) {
                lineup = fixtureLineupRepository.save(lineup);
            }
            Set<String> overrides = adminOverrideService.overriddenFields(
                    AdminOverrideTargetType.FIXTURE_LINEUP,
                    lineup.getId(),
                    LINEUP_OVERRIDE_FIELDS
            );

            lineup.updateLineup(
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "jerseyNumber", lineup.getJerseyNumber(), valueOrZero(playerInfo.getNumber())),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "position", lineup.getPosition(), valueOrUnknown(playerInfo.getPos())),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "grid", lineup.getGrid(), playerInfo.getGrid()),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "starter", lineup.isStarter(), starter)
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
        Set<String> overrides = adminOverrideService.overriddenFields(
                AdminOverrideTargetType.FIXTURE,
                fixture.getFixtureId(),
                FIXTURE_TACTIC_OVERRIDE_FIELDS
        );
        if (fixture.getHomeTeam().getTeamId().equals(team.getTeamId())) {
            fixture.updateTactics(
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "homeFormation", fixture.getHomeFormation(), lineup.getFormation()),
                    fixture.getAwayFormation(),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "homeCoachName", fixture.getHomeCoachName(), coachName),
                    fixture.getAwayCoachName()
            );
            updateHomeColors(fixture, lineup.getTeam() != null ? lineup.getTeam().getUniformColors() : null, overrides);
        }
        if (fixture.getAwayTeam().getTeamId().equals(team.getTeamId())) {
            fixture.updateTactics(
                    fixture.getHomeFormation(),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "awayFormation", fixture.getAwayFormation(), lineup.getFormation()),
                    fixture.getHomeCoachName(),
                    adminOverrideService.apiValueUnlessOverridden(
                            overrides, "awayCoachName", fixture.getAwayCoachName(), coachName)
            );
            updateAwayColors(fixture, lineup.getTeam() != null ? lineup.getTeam().getUniformColors() : null, overrides);
        }
    }

    private void updateHomeColors(
            Fixture fixture,
            ApiFootballLineupDto.UniformColors uniformColors,
            Set<String> overrides
    ) {
        ApiFootballLineupDto.ColorInfo player = uniformColors != null ? uniformColors.getPlayer() : null;
        ApiFootballLineupDto.ColorInfo goalkeeper = uniformColors != null ? uniformColors.getGoalkeeper() : null;
        fixture.updateHomeLineupColors(
                adminOverrideService.apiValueUnlessOverridden(overrides, "homePlayerColorPrimary", fixture.getHomePlayerColorPrimary(), primaryOf(player)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "homePlayerColorNumber", fixture.getHomePlayerColorNumber(), numberOf(player)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "homePlayerColorBorder", fixture.getHomePlayerColorBorder(), borderOf(player)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "homeGoalkeeperColorPrimary", fixture.getHomeGoalkeeperColorPrimary(), primaryOf(goalkeeper)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "homeGoalkeeperColorNumber", fixture.getHomeGoalkeeperColorNumber(), numberOf(goalkeeper)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "homeGoalkeeperColorBorder", fixture.getHomeGoalkeeperColorBorder(), borderOf(goalkeeper))
        );
    }

    private void updateAwayColors(
            Fixture fixture,
            ApiFootballLineupDto.UniformColors uniformColors,
            Set<String> overrides
    ) {
        ApiFootballLineupDto.ColorInfo player = uniformColors != null ? uniformColors.getPlayer() : null;
        ApiFootballLineupDto.ColorInfo goalkeeper = uniformColors != null ? uniformColors.getGoalkeeper() : null;
        fixture.updateAwayLineupColors(
                adminOverrideService.apiValueUnlessOverridden(overrides, "awayPlayerColorPrimary", fixture.getAwayPlayerColorPrimary(), primaryOf(player)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "awayPlayerColorNumber", fixture.getAwayPlayerColorNumber(), numberOf(player)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "awayPlayerColorBorder", fixture.getAwayPlayerColorBorder(), borderOf(player)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "awayGoalkeeperColorPrimary", fixture.getAwayGoalkeeperColorPrimary(), primaryOf(goalkeeper)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "awayGoalkeeperColorNumber", fixture.getAwayGoalkeeperColorNumber(), numberOf(goalkeeper)),
                adminOverrideService.apiValueUnlessOverridden(overrides, "awayGoalkeeperColorBorder", fixture.getAwayGoalkeeperColorBorder(), borderOf(goalkeeper))
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
