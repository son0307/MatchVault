package com.son.soccerStreaming.favorite.service;

import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.favorite.entity.FavoritePlayer;
import com.son.soccerStreaming.favorite.entity.FavoriteTeam;
import com.son.soccerStreaming.favorite.repository.FavoritePlayerRepository;
import com.son.soccerStreaming.favorite.repository.FavoriteTeamRepository;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private FavoriteTeamRepository favoriteTeamRepository;
    @Mock
    private FavoritePlayerRepository favoritePlayerRepository;
    @Mock
    private TeamStandingRepository teamStandingRepository;
    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void getDashboardIncludesNextAndLiveFixtureForFavoriteTeam() {
        Team favoriteTeam = Team.builder()
                .id(1L)
                .teamId(47L)
                .name("Tottenham")
                .logoUrl("tottenham.png")
                .build();
        Team opponent = Team.builder()
                .id(2L)
                .teamId(49L)
                .name("Chelsea")
                .logoUrl("chelsea.png")
                .build();
        AppUser user = AppUser.builder()
                .email("fan@example.com")
                .password("password")
                .nickname("Fan")
                .build();
        FavoriteTeam favorite = FavoriteTeam.of(user, favoriteTeam);
        Fixture nextFixture = Fixture.builder()
                .fixtureId(100L)
                .fixtureDate(LocalDateTime.of(2026, 5, 30, 12, 0))
                .homeTeam(favoriteTeam)
                .awayTeam(opponent)
                .fixtureStatus("SCHEDULED")
                .build();
        Fixture liveFixture = Fixture.builder()
                .fixtureId(200L)
                .fixtureDate(LocalDateTime.of(2026, 5, 22, 12, 0))
                .homeTeam(opponent)
                .awayTeam(favoriteTeam)
                .homeScore(1)
                .awayScore(2)
                .fixtureStatus("LIVE")
                .statusShort("2H")
                .statusLong("Second Half")
                .elapsed(63)
                .build();

        when(favoriteTeamRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(favorite));
        when(favoritePlayerRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(teamStandingRepository.findByTeamTeamIdAndSeason(47L, 2025)).thenReturn(java.util.Optional.empty());
        when(fixtureRepository.findRecentByTeam(eq(47L), eq(2025), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(fixtureRepository.findNextByTeam(eq(47L), eq(2025), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(nextFixture));
        when(fixtureRepository.findLiveByTeam(eq(47L), eq(2025), any(Pageable.class)))
                .thenReturn(List.of(liveFixture));

        var response = favoriteService.getDashboard(1L, 2025);

        assertThat(response.getTeams()).hasSize(1);
        var teamCard = response.getTeams().get(0);
        assertThat(teamCard.getNextFixture().getFixtureId()).isEqualTo(100L);
        assertThat(teamCard.getNextFixture().getHomeTeamName()).isEqualTo("Tottenham");
        assertThat(teamCard.getLiveFixture().getFixtureId()).isEqualTo(200L);
        assertThat(teamCard.getLiveFixture().getHomeScore()).isEqualTo(1);
        assertThat(teamCard.getLiveFixture().getAwayScore()).isEqualTo(2);
        assertThat(teamCard.getLiveFixture().getElapsed()).isEqualTo(63);
    }

    @Test
    void getDashboardUsesRequestedSeasonForFavoritePlayerStats() {
        AppUser user = AppUser.builder()
                .email("fan@example.com")
                .password("password")
                .nickname("Fan")
                .build();
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .position("F")
                .photoUrl("son.png")
                .build();
        Team team = Team.builder()
                .id(1L)
                .teamId(47L)
                .name("Tottenham")
                .logoUrl("tottenham.png")
                .build();
        FavoritePlayer favorite = FavoritePlayer.of(user, player);
        PlayerTeamSeasonStat seasonStat = PlayerTeamSeasonStat.builder()
                .player(player)
                .team(team)
                .leagueId(39L)
                .season(2024)
                .appearances(30)
                .minutes(2400)
                .rating(7.4)
                .goals(17)
                .assists(10)
                .yellowCards(1)
                .redCards(0)
                .build();

        when(favoriteTeamRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(favoritePlayerRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(favorite));
        when(playerFixtureStatRepository.findTop5ByPlayerPlayerIdOrderByFixtureFixtureDateDesc(7L)).thenReturn(List.of());
        when(playerTeamSeasonStatRepository.findAllByPlayerPlayerIdAndSeason(7L, 2024))
                .thenReturn(List.of(seasonStat));

        var response = favoriteService.getDashboard(1L, 2024);

        assertThat(response.getPlayers()).hasSize(1);
        var playerCard = response.getPlayers().get(0);
        assertThat(playerCard.getSeasonStat().getSeason()).isEqualTo(2024);
        assertThat(playerCard.getSeasonStat().getGoals()).isEqualTo(17);
        assertThat(playerCard.getSeasonStat().getAssists()).isEqualTo(10);
        assertThat(playerCard.getSeasonStat().getRating()).isEqualTo(7.4);
    }

    @Test
    void getDashboardReturnsEmptyPlayerSeasonStatWhenRequestedSeasonHasNoRecord() {
        AppUser user = AppUser.builder()
                .email("fan@example.com")
                .password("password")
                .nickname("Fan")
                .build();
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .position("F")
                .build();
        FavoritePlayer favorite = FavoritePlayer.of(user, player);

        when(favoriteTeamRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(favoritePlayerRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(favorite));
        when(playerFixtureStatRepository.findTop5ByPlayerPlayerIdOrderByFixtureFixtureDateDesc(7L)).thenReturn(List.of());
        when(playerTeamSeasonStatRepository.findAllByPlayerPlayerIdAndSeason(7L, 2023))
                .thenReturn(List.of());

        var response = favoriteService.getDashboard(1L, 2023);

        assertThat(response.getPlayers()).hasSize(1);
        assertThat(response.getPlayers().get(0).getSeasonStat()).isNull();
    }
}
