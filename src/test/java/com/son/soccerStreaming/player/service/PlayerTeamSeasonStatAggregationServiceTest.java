package com.son.soccerStreaming.player.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerTeamSeasonStatAggregationServiceTest {

    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private FixtureLineupRepository fixtureLineupRepository;
    @Mock
    private PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;

    @InjectMocks
    private PlayerTeamSeasonStatAggregationService service;

    @Test
    void rebuildSeasonSeparatesPalmerStatsByActualFixtureTeamAndIsIdempotent() {
        Player palmer = player(152982L, "C. Palmer", "Midfielder");
        Team manchesterCity = team(50L, "Manchester City");
        Team chelsea = team(49L, "Chelsea");
        PlayerTeamSeasonStat citySeason = seasonStat(palmer, manchesterCity, 2023);
        PlayerTeamSeasonStat chelseaSeason = seasonStat(palmer, chelsea, 2023);
        List<PlayerFixtureStat> stats = new ArrayList<>();
        List<FixtureLineup> lineups = new ArrayList<>();

        Fixture cityFixture = fixture(1L, 2023, manchesterCity, chelsea, 1);
        stats.add(stat(cityFixture, manchesterCity, palmer, 10, true, 0, 0, 7.0));
        lineups.add(lineup(cityFixture, manchesterCity, palmer, 80, "M", false));

        for (int index = 0; index < 33; index++) {
            Fixture fixture = fixture(100L + index, 2023, chelsea, manchesterCity, 2 + index);
            int minutes = index == 32 ? 90 : 79;
            stats.add(stat(
                    fixture,
                    chelsea,
                    palmer,
                    minutes,
                    index >= 30,
                    index < 22 ? 1 : 0,
                    index < 11 ? 1 : 0,
                    index == 5 ? null : 7.7
            ));
            lineups.add(lineup(fixture, chelsea, palmer, 20, "M", index < 30));
        }

        when(playerFixtureStatRepository.findAllByFixtureSeason(2023)).thenReturn(stats);
        when(fixtureLineupRepository.findAllBySeason(2023)).thenReturn(lineups);
        when(playerTeamSeasonStatRepository.findByPlayerPlayerIdAndTeamTeamIdAndLeagueIdAndSeason(
                152982L, 50L, 39L, 2023
        )).thenReturn(Optional.of(citySeason));
        when(playerTeamSeasonStatRepository.findByPlayerPlayerIdAndTeamTeamIdAndLeagueIdAndSeason(
                152982L, 49L, 39L, 2023
        )).thenReturn(Optional.of(chelseaSeason));

        service.rebuildSeason(39, 2023);
        service.rebuildSeason(39, 2023);

        assertThat(citySeason.getAppearances()).isEqualTo(1);
        assertThat(citySeason.getMinutes()).isEqualTo(10);
        assertThat(citySeason.getGoals()).isZero();
        assertThat(citySeason.getAssists()).isZero();
        assertThat(citySeason.getSubstitutesIn()).isEqualTo(1);

        assertThat(chelseaSeason.getAppearances()).isEqualTo(33);
        assertThat(chelseaSeason.getMinutes()).isEqualTo(2618);
        assertThat(chelseaSeason.getGoals()).isEqualTo(22);
        assertThat(chelseaSeason.getAssists()).isEqualTo(11);
        assertThat(chelseaSeason.getLineups()).isEqualTo(30);
        assertThat(chelseaSeason.getSubstitutesIn()).isEqualTo(3);
        assertThat(chelseaSeason.getBackNumber()).isEqualTo(20);
        assertThat(chelseaSeason.getPosition()).isEqualTo("M");
    }

    @Test
    void rebuildSeasonCreatesBenchOnlyRowAndAggregatesGoalkeeperStats() {
        Team everton = team(45L, "Everton");
        Team arsenal = team(42L, "Arsenal");
        Player benchPlayer = player(10L, "Bench Player", "Defender");
        Player goalkeeper = player(20L, "Goalkeeper", "Goalkeeper");
        PlayerTeamSeasonStat benchSeason = seasonStat(benchPlayer, everton, 2024);
        PlayerTeamSeasonStat keeperSeason = seasonStat(goalkeeper, everton, 2024);

        Fixture benchFixture = fixture(10L, 2024, everton, arsenal, 1);
        Fixture keeperFixture = fixture(11L, 2024, everton, arsenal, 2);
        FixtureLineup benchLineup = lineup(benchFixture, everton, benchPlayer, 4, "D", false);
        FixtureLineup keeperLineup = lineup(keeperFixture, everton, goalkeeper, 1, "G", true);
        PlayerFixtureStat keeperStat = PlayerFixtureStat.builder()
                .fixture(keeperFixture)
                .team(everton)
                .player(goalkeeper)
                .minutesPlayed(90)
                .rating(null)
                .isSubstitute(false)
                .conceded(2)
                .saves(8)
                .passesTotal(40)
                .passesAccurate(30)
                .yellowCards(2)
                .redCards(1)
                .penaltySaved(1)
                .build();

        when(playerFixtureStatRepository.findAllByFixtureSeason(2024)).thenReturn(List.of(keeperStat));
        when(fixtureLineupRepository.findAllBySeason(2024)).thenReturn(List.of(benchLineup, keeperLineup));
        when(playerTeamSeasonStatRepository.findByPlayerPlayerIdAndTeamTeamIdAndLeagueIdAndSeason(
                10L, 45L, 39L, 2024
        )).thenReturn(Optional.of(benchSeason));
        when(playerTeamSeasonStatRepository.findByPlayerPlayerIdAndTeamTeamIdAndLeagueIdAndSeason(
                20L, 45L, 39L, 2024
        )).thenReturn(Optional.of(keeperSeason));

        service.rebuildSeason(39, 2024);
        service.rebuildSeason(39, 2024);

        assertThat(benchSeason.getAppearances()).isZero();
        assertThat(benchSeason.getSubstitutesBench()).isEqualTo(1);
        assertThat(benchSeason.getBackNumber()).isEqualTo(4);
        assertThat(benchSeason.getPosition()).isEqualTo("D");

        assertThat(keeperSeason.getAppearances()).isEqualTo(1);
        assertThat(keeperSeason.getMinutes()).isEqualTo(90);
        assertThat(keeperSeason.getRating()).isNull();
        assertThat(keeperSeason.getSaves()).isEqualTo(8);
        assertThat(keeperSeason.getConceded()).isEqualTo(2);
        assertThat(keeperSeason.getPassAccuracy()).isEqualTo(75);
        assertThat(keeperStat.getYellowCards()).isZero();
        assertThat(keeperStat.getRedCards()).isEqualTo(1);
        assertThat(keeperSeason.getYellowCards()).isZero();
        assertThat(keeperSeason.getRedCards()).isEqualTo(1);
        assertThat(keeperSeason.getPenaltySaved()).isEqualTo(1);
    }

    private PlayerFixtureStat stat(
            Fixture fixture,
            Team team,
            Player player,
            int minutes,
            boolean substitute,
            int goals,
            int assists,
            Double rating
    ) {
        return PlayerFixtureStat.builder()
                .fixture(fixture)
                .team(team)
                .player(player)
                .minutesPlayed(minutes)
                .isSubstitute(substitute)
                .goals(goals)
                .assists(assists)
                .rating(rating)
                .shotsTotal(goals + 1)
                .shotsOnTarget(goals)
                .passesTotal(10)
                .passesAccurate(8)
                .passesKey(assists)
                .build();
    }

    private FixtureLineup lineup(
            Fixture fixture,
            Team team,
            Player player,
            int number,
            String position,
            boolean starter
    ) {
        return FixtureLineup.builder()
                .fixture(fixture)
                .team(team)
                .player(player)
                .jerseyNumber(number)
                .position(position)
                .isStarter(starter)
                .build();
    }

    private Fixture fixture(Long id, int season, Team home, Team away, int day) {
        return Fixture.builder()
                .fixtureId(id)
                .season(season)
                .fixtureDate(LocalDateTime.of(2024, 1, Math.min(day, 28), 15, 0))
                .homeTeam(home)
                .awayTeam(away)
                .build();
    }

    private Player player(Long id, String name, String position) {
        return Player.builder().playerId(id).name(name).position(position).build();
    }

    private Team team(Long id, String name) {
        return Team.builder().teamId(id).name(name).build();
    }

    private PlayerTeamSeasonStat seasonStat(Player player, Team team, int season) {
        return PlayerTeamSeasonStat.builder()
                .player(player)
                .team(team)
                .leagueId(39L)
                .season(season)
                .build();
    }
}
