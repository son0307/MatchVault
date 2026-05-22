package com.son.soccerStreaming.home.service;

import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.favorite.service.FavoriteService;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import com.son.soccerStreaming.home.dto.HomeSummaryResponseDto;
import com.son.soccerStreaming.team.dto.TeamStandingResponseDto;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.service.TeamStandingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private FixtureRecordRepository fixtureRecordRepository;
    @Mock
    private TeamStandingService teamStandingService;
    @Mock
    private FavoriteService favoriteService;

    @InjectMocks
    private HomeService homeService;

    @Test
    void getSummaryReturnsTodayFixturesStandingsAndFavoritesForLoggedInUser() {
        Team homeTeam = Team.builder().teamId(47L).name("Tottenham").build();
        Team awayTeam = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(100L)
                .fixtureDate(LocalDateTime.of(2026, 5, 22, 12, 0))
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeScore(2)
                .awayScore(1)
                .fixtureStatus("FT")
                .build();
        TeamStandingResponseDto standing = TeamStandingResponseDto.builder()
                .season(2025)
                .rank(1)
                .points(80)
                .build();
        FavoriteDashboardResponseDto favorites = FavoriteDashboardResponseDto.builder()
                .teams(List.of(FavoriteDashboardResponseDto.TeamCard.builder()
                        .teamId(47L)
                        .teamName("Tottenham")
                        .build()))
                .players(List.of())
                .build();

        when(fixtureRecordRepository.findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(List.of(fixture));
        when(teamStandingService.getStandings(2025)).thenReturn(List.of(standing));
        when(favoriteService.getDashboard(1L, 2025)).thenReturn(favorites);

        HomeSummaryResponseDto response = homeService.getSummary(2025, 1L);

        assertThat(response.getTodayFixtures()).extracting("fixtureId").containsExactly(100L);
        assertThat(response.getTodayFixtures().get(0).getHomeTeamName()).isEqualTo("Tottenham");
        assertThat(response.getStandings()).containsExactly(standing);
        assertThat(response.getFavorites()).isSameAs(favorites);
    }

    @Test
    void getSummaryUsesKoreaTodayUtcRange() {
        when(fixtureRecordRepository.findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(teamStandingService.getStandings(2025)).thenReturn(List.of());

        homeService.getSummary(2025, null);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fixtureRecordRepository).findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
                org.mockito.ArgumentMatchers.eq(2025),
                startCaptor.capture(),
                endCaptor.capture()
        );
        assertThat(endCaptor.getValue()).isEqualTo(startCaptor.getValue().plusDays(1));
        assertThat(startCaptor.getValue().getHour()).isEqualTo(15);
        assertThat(startCaptor.getValue().getMinute()).isZero();
    }

    @Test
    void getSummaryReturnsEmptyFavoritesForGuest() {
        when(fixtureRecordRepository.findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(teamStandingService.getStandings(2025)).thenReturn(List.of());

        HomeSummaryResponseDto response = homeService.getSummary(2025, null);

        assertThat(response.getFavorites().getTeams()).isEmpty();
        assertThat(response.getFavorites().getPlayers()).isEmpty();
        verify(favoriteService, never()).getDashboard(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }
}
