package com.son.soccerStreaming.league.service;

import com.son.soccerStreaming.fixture.repository.FixtureStatRepository;
import com.son.soccerStreaming.league.dto.LeagueTeamRankingResponseDto;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueTeamRankingServiceTest {

    @Mock
    private TeamStandingRepository teamStandingRepository;
    @Mock
    private FixtureStatRepository fixtureStatRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private LeagueTeamRankingService service;

    @Test
    void ranksCategoriesAndCalculatesPerMatchAverages() {
        Team arsenal = team(42L, "Arsenal");
        Team chelsea = team(49L, "Chelsea");
        Team liverpool = team(40L, "Liverpool");

        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                standing(arsenal, 2, 10, 20, 8),
                standing(chelsea, 1, 10, 20, 8),
                standing(liverpool, 3, 0, 0, 5)
        ));
        when(fixtureStatRepository.findTeamSeasonStatAggregates(39, 2025)).thenReturn(List.of(
                aggregate(42L, 55.126, 14L, 1L),
                aggregate(49L, 55.126, 12L, 3L)
        ));

        LeagueTeamRankingResponseDto response = service.getRankings(39, 2025);

        assertThat(response.getGoalsFor())
                .extracting(LeagueTeamRankingResponseDto.Row::getTeamId)
                .containsExactly(49L, 42L, 40L);
        assertThat(response.getGoalsAgainst())
                .extracting(LeagueTeamRankingResponseDto.Row::getTeamId)
                .containsExactly(40L, 49L, 42L);
        assertThat(response.getPossession())
                .extracting(LeagueTeamRankingResponseDto.Row::getTeamId)
                .containsExactly(49L, 42L);
        assertThat(response.getYellowCards())
                .extracting(LeagueTeamRankingResponseDto.Row::getTeamId)
                .containsExactly(42L, 49L, 40L);
        assertThat(response.getRedCards())
                .extracting(LeagueTeamRankingResponseDto.Row::getTeamId)
                .containsExactly(49L, 42L, 40L);

        assertThat(response.getGoalsFor().get(0)).satisfies(row -> {
            assertThat(row.getRank()).isEqualTo(1);
            assertThat(row.getGoalsForPerMatch()).isEqualTo(2.0);
            assertThat(row.getGoalsAgainstPerMatch()).isEqualTo(0.8);
            assertThat(row.getAveragePossession()).isEqualTo(55.13);
        });
        assertThat(response.getGoalsFor().get(2)).satisfies(row -> {
            assertThat(row.getPlayed()).isZero();
            assertThat(row.getGoalsForPerMatch()).isZero();
            assertThat(row.getGoalsAgainstPerMatch()).isZero();
            assertThat(row.getAveragePossession()).isNull();
            assertThat(row.getYellowCards()).isZero();
            assertThat(row.getRedCards()).isZero();
        });
    }

    @Test
    void returnsEmptyCategoriesWhenStandingsAreMissing() {
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2024)).thenReturn(List.of());

        LeagueTeamRankingResponseDto response = service.getRankings(39, 2024);

        assertThat(response.getGoalsFor()).isEmpty();
        assertThat(response.getGoalsAgainst()).isEmpty();
        assertThat(response.getPossession()).isEmpty();
        assertThat(response.getYellowCards()).isEmpty();
        assertThat(response.getRedCards()).isEmpty();
    }

    @Test
    void returnsEveryTeamWithoutTopTwentyLimit() {
        List<TeamStanding> standings = java.util.stream.IntStream.rangeClosed(1, 24)
                .mapToObj(index -> standing(
                        team((long) index, "Team " + index),
                        index,
                        10,
                        100 - index,
                        index
                ))
                .toList();
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(standings);
        when(fixtureStatRepository.findTeamSeasonStatAggregates(39, 2025)).thenReturn(List.of());

        LeagueTeamRankingResponseDto response = service.getRankings(39, 2025);

        assertThat(response.getGoalsFor()).hasSize(24);
        assertThat(response.getGoalsAgainst()).hasSize(24);
        assertThat(response.getYellowCards()).hasSize(24);
        assertThat(response.getRedCards()).hasSize(24);
    }

    private Team team(Long id, String name) {
        return Team.builder().teamId(id).name(name).build();
    }

    private TeamStanding standing(
            Team team,
            int rank,
            int played,
            int goalsFor,
            int goalsAgainst
    ) {
        return TeamStanding.builder()
                .team(team)
                .leagueId(39)
                .season(2025)
                .rank(rank)
                .played(played)
                .goalsFor(goalsFor)
                .goalsAgainst(goalsAgainst)
                .build();
    }

    private FixtureStatRepository.TeamSeasonStatAggregate aggregate(
            Long teamId,
            Double averagePossession,
            Long yellowCards,
            Long redCards
    ) {
        return new FixtureStatRepository.TeamSeasonStatAggregate() {
            public Long getTeamId() { return teamId; }
            public Double getAveragePossession() { return averagePossession; }
            public Long getYellowCards() { return yellowCards; }
            public Long getRedCards() { return redCards; }
        };
    }
}
