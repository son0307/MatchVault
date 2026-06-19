package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballPlayerDto;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.media.service.ImageCacheService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.player.service.PlayerTeamSeasonStatAggregationService;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballPlayerSyncProfileOnlyTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private FixtureLineupRepository fixtureLineupRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private AdminOverrideService adminOverrideService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private EntityManager entityManager;
    @Mock private ApiFootballSyncStatusService apiFootballSyncStatusService;
    @Mock private ImageCacheService imageCacheService;
    @Mock private PlayerTeamSeasonStatAggregationService aggregationService;

    @InjectMocks
    private ApiFootballPlayerSyncService service;

    @Test
    void updatesProfileFromExactLeagueStatWithoutStoringSeasonTotals() {
        Team chelsea = Team.builder().teamId(49L).name("Chelsea").build();
        ApiFootballPlayerDto.RegisteredPlayerResponse response =
                mock(ApiFootballPlayerDto.RegisteredPlayerResponse.class);
        ApiFootballPlayerDto.ProfilePlayer profile = mock(ApiFootballPlayerDto.ProfilePlayer.class);
        ApiFootballPlayerDto.PlayerStatistics cupStat = playerStat(49L, 45L, 2023, "Wrong", 99);
        ApiFootballPlayerDto.PlayerStatistics leagueStat = playerStat(49L, 39L, 2023, "Midfielder", 20);

        when(response.getPlayer()).thenReturn(profile);
        when(response.getStatistics()).thenReturn(List.of(cupStat, leagueStat));
        when(profile.getId()).thenReturn(152982L);
        when(profile.getName()).thenReturn("C. Palmer");
        when(playerRepository.findByPlayerId(152982L)).thenReturn(Optional.empty());
        when(adminOverrideService.overriddenFields(any(), any(), any())).thenReturn(Set.of());
        when(adminOverrideService.apiValueUnlessOverridden(anySet(), anyString(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertRegisteredPlayer(response, chelsea, 39, 2023);

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue().getPosition()).isEqualTo("Midfielder");
        assertThat(playerCaptor.getValue().getNumber()).isEqualTo(20);
        verify(playerTeamSeasonStatRepository, never()).save(any());
    }

    private ApiFootballPlayerDto.PlayerStatistics playerStat(
            Long teamId,
            Long leagueId,
            Integer season,
            String position,
            Integer number
    ) {
        ApiFootballPlayerDto.PlayerStatistics stat = mock(ApiFootballPlayerDto.PlayerStatistics.class);
        ApiFootballPlayerDto.TeamInfo team = mock(ApiFootballPlayerDto.TeamInfo.class);
        ApiFootballPlayerDto.LeagueInfo league = mock(ApiFootballPlayerDto.LeagueInfo.class);
        ApiFootballPlayerDto.Games games = mock(ApiFootballPlayerDto.Games.class);
        when(team.getId()).thenReturn(teamId);
        when(league.getId()).thenReturn(leagueId);
        lenient().when(league.getSeason()).thenReturn(season);
        lenient().when(games.getPosition()).thenReturn(position);
        lenient().when(games.getNumber()).thenReturn(number);
        when(stat.getTeam()).thenReturn(team);
        when(stat.getLeague()).thenReturn(league);
        lenient().when(stat.getGames()).thenReturn(games);
        return stat;
    }
}
