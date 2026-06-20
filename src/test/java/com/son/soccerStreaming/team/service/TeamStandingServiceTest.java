package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamStandingServiceTest {

    @Mock
    private TeamStandingRepository teamStandingRepository;
    @Mock
    private ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private TeamStandingService teamStandingService;

    @Test
    void getStandingsIncludesRecentFiveFormFromFinishedFixtures() {
        Team arsenal = team(42L, "Arsenal");
        Team city = team(50L, "Manchester City");
        Team chelsea = team(49L, "Chelsea");
        Team spurs = team(47L, "Tottenham");

        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                standing(arsenal, 1, 80),
                standing(city, 2, 78)
        ));
        when(apiFootballStandingLocalUpdateService.findImpacts(2025)).thenReturn(List.of());
        when(fixtureRepository.findFinishedWithScoresBySeasonOrderByFixtureDateDesc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.anyCollection()
        ))
                .thenReturn(List.of(
                        fixture(1L, arsenal, city, 2, 1, 10),
                        fixture(2L, chelsea, arsenal, 0, 0, 9),
                        fixture(3L, arsenal, spurs, 1, 3, 8),
                        fixture(4L, city, arsenal, 1, 2, 7),
                        fixture(5L, arsenal, chelsea, 4, 0, 6),
                        fixture(6L, spurs, arsenal, 5, 0, 5),
                        fixture(7L, city, spurs, 3, 0, 4)
                ));

        var response = teamStandingService.getStandings(2025);

        var arsenalForm = response.get(0).getRecentForm();
        assertThat(arsenalForm.getPlayed()).isEqualTo(5);
        assertThat(arsenalForm.getWin()).isEqualTo(3);
        assertThat(arsenalForm.getDraw()).isEqualTo(1);
        assertThat(arsenalForm.getLose()).isEqualTo(1);
        assertThat(arsenalForm.getGoals().getGoalsFor()).isEqualTo(9);
        assertThat(arsenalForm.getGoals().getGoalsAgainst()).isEqualTo(5);
        assertThat(arsenalForm.getGoalsDiff()).isEqualTo(4);
        assertThat(arsenalForm.getPoints()).isEqualTo(10);
        assertThat(arsenalForm.getResults()).containsExactly("W", "D", "L", "W", "W");

        var cityForm = response.get(1).getRecentForm();
        assertThat(cityForm.getPlayed()).isEqualTo(3);
        assertThat(cityForm.getWin()).isEqualTo(1);
        assertThat(cityForm.getDraw()).isZero();
        assertThat(cityForm.getLose()).isEqualTo(2);
        assertThat(cityForm.getResults()).containsExactly("L", "L", "W");
    }

    private Team team(Long teamId, String name) {
        return Team.builder()
                .teamId(teamId)
                .name(name)
                .logoUrl(name + ".png")
                .build();
    }

    private TeamStanding standing(Team team, Integer rank, Integer points) {
        return TeamStanding.builder()
                .team(team)
                .season(2025)
                .rank(rank)
                .points(points)
                .goalsDiff(points - 60)
                .played(38)
                .win(20)
                .draw(8)
                .lose(10)
                .goalsFor(70)
                .goalsAgainst(40)
                .homePlayed(19)
                .homeWin(12)
                .homeDraw(4)
                .homeLose(3)
                .homeGoalsFor(38)
                .homeGoalsAgainst(17)
                .awayPlayed(19)
                .awayWin(8)
                .awayDraw(4)
                .awayLose(7)
                .awayGoalsFor(32)
                .awayGoalsAgainst(23)
                .form("WWDLW")
                .build();
    }

    private Fixture fixture(Long fixtureId, Team home, Team away, Integer homeScore, Integer awayScore, int daysAgo) {
        return Fixture.builder()
                .fixtureId(fixtureId)
                .homeTeam(home)
                .awayTeam(away)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .fixtureStatus("FINISHED")
                .fixtureDate(LocalDateTime.of(2026, 5, 28, 12, 0).minusDays(daysAgo))
                .season(2025)
                .build();
    }
}
