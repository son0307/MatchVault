package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationRankingReadStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.dto.TeamResponseDto;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    @Mock
    private MediaUrlService mediaUrlService;
    @Mock
    private TeamPlayerRankingService teamPlayerRankingService;
    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private FixtureDetailGenerationResolver generationResolver;
    @Mock
    private FixtureDetailGenerationRankingReadStore generationRankingReadStore;

    @InjectMocks
    private TeamService teamService;

    @Test
    void getPlayerRankingsDelegatesAfterTeamExists() {
        Team tottenham = Team.builder()
                .id(1L)
                .teamId(47L)
                .name("Tottenham")
                .build();
        TeamResponseDto.PlayerRankings expected = TeamResponseDto.PlayerRankings.builder()
                .rows(java.util.List.of())
                .build();

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(tottenham));
        when(teamPlayerRankingService.getPlayerRankings(eq(47L), eq(2025))).thenReturn(expected);

        TeamResponseDto.PlayerRankings rankings = teamService.getPlayerRankings(47L, 2025);

        assertThat(rankings).isSameAs(expected);
    }

    @Test
    void getPlayersByTeamUsesOnePublishedGenerationForStatsAndLatestTeam() {
        Player player = Player.builder().id(2L).playerId(9L).name("Player").build();
        PlayerTeamSeasonStat stat = PlayerTeamSeasonStat.builder().player(player).build();
        PlayerFixtureStatRepository.LatestPlayerTeam latest = org.mockito.Mockito.mock(
                PlayerFixtureStatRepository.LatestPlayerTeam.class);
        when(latest.getPlayerId()).thenReturn(9L);
        when(latest.getTeamId()).thenReturn(47L);
        when(generationResolver.enabled()).thenReturn(true);
        when(generationResolver.activeForSeason(2025)).thenReturn(7L);
        when(generationRankingReadStore.teamStats(7L, 47L, 2025)).thenReturn(List.of(stat));
        when(generationRankingReadStore.latestTeams(7L, 2025, List.of(9L))).thenReturn(List.of(latest));

        assertThat(teamService.getPlayersByTeam(47L, 2025)).hasSize(1);
        verify(playerTeamSeasonStatRepository, never()).findAllByTeamAndSeason(47L, 2025);
        verify(playerFixtureStatRepository, never())
                .findTeamHistoryByPlayerIdsAndSeasonOrderByLatest(List.of(9L), 2025);
    }
}
