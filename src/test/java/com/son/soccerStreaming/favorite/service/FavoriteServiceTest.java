package com.son.soccerStreaming.favorite.service;

import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.favorite.entity.FavoritePlayer;
import com.son.soccerStreaming.favorite.entity.FavoriteTeam;
import com.son.soccerStreaming.favorite.repository.FavoritePlayerRepository;
import com.son.soccerStreaming.favorite.repository.FavoriteTeamRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private FavoriteCardService favoriteCardService;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void getDashboardBuildsCardsFromSharedCardCacheService() {
        Team team = Team.builder()
                .teamId(47L)
                .name("Tottenham")
                .build();
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .build();
        FavoriteTeam favoriteTeam = FavoriteTeam.of(null, team);
        FavoritePlayer favoritePlayer = FavoritePlayer.of(null, player);
        FavoriteDashboardResponseDto.TeamCard teamCard = FavoriteDashboardResponseDto.TeamCard.builder()
                .teamId(47L)
                .teamName("Tottenham")
                .build();
        FavoriteDashboardResponseDto.PlayerCard playerCard = FavoriteDashboardResponseDto.PlayerCard.builder()
                .playerId(7L)
                .playerName("Son")
                .build();

        when(favoriteTeamRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(favoriteTeam));
        when(favoritePlayerRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(favoritePlayer));
        when(favoriteCardService.getTeamCard(47L, 2025)).thenReturn(teamCard);
        when(favoriteCardService.getPlayerCard(7L, 2025)).thenReturn(playerCard);

        FavoriteDashboardResponseDto response = favoriteService.getDashboard(1L, 2025);

        assertThat(response.getTeams()).containsExactly(teamCard);
        assertThat(response.getPlayers()).containsExactly(playerCard);
    }
}
