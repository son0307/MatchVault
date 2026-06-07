package com.son.soccerStreaming.player.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.dto.PlayerResponseDto;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void getPlayerPanelFiltersSeasonSummaryTeamsByActualMatchTeams() {
        Player player = Player.builder()
                .playerId(1165L)
                .name("Matheus Cunha")
                .build();
        Team manchesterUnited = Team.builder()
                .id(1L)
                .teamId(33L)
                .name("Manchester United")
                .build();
        Team wolves = Team.builder()
                .id(2L)
                .teamId(39L)
                .name("Wolves")
                .build();
        Team chelsea = Team.builder()
                .id(3L)
                .teamId(49L)
                .name("Chelsea")
                .build();

        PlayerTeamSeasonStat pollutedUnitedStat = seasonStat(player, manchesterUnited, 2024, 33, 2600, 15);
        PlayerTeamSeasonStat wolvesStat = seasonStat(player, wolves, 2024, 33, 2603, 15);
        PlayerTeamSeasonStat currentUnitedStat = seasonStat(player, manchesterUnited, 2025, 10, 800, 3);

        Fixture wolvesFixture = fixture(100L, 2024, wolves, chelsea);
        Fixture unitedFixture = fixture(101L, 2025, manchesterUnited, chelsea);
        PlayerFixtureStat wolvesMatch = PlayerFixtureStat.builder()
                .player(player)
                .team(wolves)
                .fixture(wolvesFixture)
                .build();
        PlayerFixtureStat unitedMatch = PlayerFixtureStat.builder()
                .player(player)
                .team(manchesterUnited)
                .fixture(unitedFixture)
                .build();

        when(playerRepository.findByPlayerId(1165L)).thenReturn(Optional.of(player));
        when(playerFixtureStatRepository.findAllByPlayerPlayerIdOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(1165L))
                .thenReturn(List.of(unitedMatch, wolvesMatch));
        when(playerTeamSeasonStatRepository.findAllByPlayerPlayerIdOrderBySeasonDesc(1165L))
                .thenReturn(List.of(currentUnitedStat, pollutedUnitedStat, wolvesStat));

        PlayerResponseDto.Panel panel = playerService.getPlayerPanel(1165L);

        PlayerResponseDto.SeasonSummary season2024 = panel.getSeasons().stream()
                .filter(season -> season.getSeason().equals(2024))
                .findFirst()
                .orElseThrow();
        assertThat(season2024.getTeams()).extracting(PlayerResponseDto.TeamSeasonSummary::getTeamName)
                .containsExactly("Wolves");
        assertThat(season2024.getTotalFixtures()).isEqualTo(33);
        assertThat(season2024.getMinutesPlayed()).isEqualTo(2603);
        assertThat(season2024.getGoals()).isEqualTo(15);
    }

    @Test
    void getPlayerPanelUsesLatestMatchTeamForProfile() {
        Player player = Player.builder()
                .playerId(47L)
                .name("Brennan Johnson")
                .build();
        Team tottenham = Team.builder()
                .id(1L)
                .teamId(47L)
                .name("Tottenham")
                .logoUrl("tottenham.png")
                .build();
        Team crystalPalace = Team.builder()
                .id(2L)
                .teamId(52L)
                .name("Crystal Palace")
                .logoUrl("palace.png")
                .build();
        Team arsenal = Team.builder()
                .id(3L)
                .teamId(42L)
                .name("Arsenal")
                .build();

        PlayerFixtureStat latestPalaceMatch = PlayerFixtureStat.builder()
                .player(player)
                .team(crystalPalace)
                .fixture(fixture(200L, 2025, crystalPalace, arsenal))
                .build();
        PlayerFixtureStat olderTottenhamMatch = PlayerFixtureStat.builder()
                .player(player)
                .team(tottenham)
                .fixture(fixture(199L, 2025, tottenham, arsenal))
                .build();

        when(playerRepository.findByPlayerId(47L)).thenReturn(Optional.of(player));
        when(playerFixtureStatRepository.findAllByPlayerPlayerIdOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(47L))
                .thenReturn(List.of(latestPalaceMatch, olderTottenhamMatch));
        when(playerTeamSeasonStatRepository.findAllByPlayerPlayerIdOrderBySeasonDesc(47L))
                .thenReturn(List.of(
                        seasonStat(player, tottenham, 2025, 5, 320, 1),
                        seasonStat(player, crystalPalace, 2025, 7, 510, 3)
                ));
        when(mediaUrlService.teamLogoUrl(any(Team.class)))
                .thenAnswer(invocation -> invocation.<Team>getArgument(0).getLogoUrl());

        PlayerResponseDto.Panel panel = playerService.getPlayerPanel(47L);

        assertThat(panel.getProfile().getTeamId()).isEqualTo(52L);
        assertThat(panel.getProfile().getTeamName()).isEqualTo("Crystal Palace");
        assertThat(panel.getProfile().getTeamLogoUrl()).isEqualTo("palace.png");
    }

    private PlayerTeamSeasonStat seasonStat(Player player, Team team, Integer season,
                                            Integer appearances, Integer minutes, Integer goals) {
        return PlayerTeamSeasonStat.builder()
                .player(player)
                .team(team)
                .leagueId(39L)
                .season(season)
                .appearances(appearances)
                .minutes(minutes)
                .goals(goals)
                .build();
    }

    private Fixture fixture(Long fixtureId, Integer season, Team homeTeam, Team awayTeam) {
        return Fixture.builder()
                .fixtureId(fixtureId)
                .season(season)
                .fixtureDate(LocalDateTime.of(season, 8, 1, 12, 0))
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeScore(1)
                .awayScore(0)
                .build();
    }
}
