package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import com.son.soccerStreaming.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveFixtureQueryServiceTest {

    @Mock
    private FixtureRecordRepository fixtureRecordRepository;

    @InjectMocks
    private LiveFixtureQueryService liveFixtureQueryService;

    @Test
    void getTodayLiveFixturesReturnsLiveFixturesInSummaryShape() {
        Team homeTeam = Team.builder().teamId(47L).name("Tottenham").build();
        Team awayTeam = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(100L)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeScore(1)
                .awayScore(0)
                .fixtureStatus("LIVE")
                .build();

        when(fixtureRecordRepository.findAllBySeasonAndFixtureStatusOrderByFixtureDateAsc(2025, "LIVE"))
                .thenReturn(List.of(fixture));

        var response = liveFixtureQueryService.getTodayLiveFixtures(2025);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getFixtureId()).isEqualTo(100L);
        assertThat(response.get(0).getHomeTeamName()).isEqualTo("Tottenham");
        assertThat(response.get(0).getAwayTeamName()).isEqualTo("Chelsea");
        assertThat(response.get(0).getFixtureStatus()).isEqualTo("LIVE");
    }

    @Test
    void getTodayLiveFixturesUsesSeasonAndLiveStatus() {
        when(fixtureRecordRepository.findAllBySeasonAndFixtureStatusOrderByFixtureDateAsc(2025, "LIVE"))
                .thenReturn(List.of());

        var response = liveFixtureQueryService.getTodayLiveFixtures(2025);

        assertThat(response).isEmpty();
    }
}
