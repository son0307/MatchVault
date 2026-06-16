package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.dto.TeamResponseDto;
import com.son.soccerStreaming.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamPlayerRankingServiceTest {

    @Mock
    private PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private TeamPlayerRankingService teamPlayerRankingService;

    @Test
    void getPlayerRankingsKeepsOnlyPlayersWhoseLatestMatchBelongsToTeam() {
        Team tottenham = Team.builder()
                .id(1L)
                .teamId(47L)
                .name("Tottenham")
                .build();
        Player currentPlayer = Player.builder()
                .playerId(10L)
                .name("Current Player")
                .position("F")
                .build();
        Player transferredPlayer = Player.builder()
                .playerId(20L)
                .name("Transferred Player")
                .position("M")
                .build();
        PlayerTeamSeasonStat currentStat = seasonStat(currentPlayer, tottenham, 2025, 8, 2, 720);
        PlayerTeamSeasonStat transferredStat = seasonStat(transferredPlayer, tottenham, 2025, 6, 1, 540);

        when(playerTeamSeasonStatRepository.findAllByTeamAndSeason(47L, 2025))
                .thenReturn(List.of(currentStat, transferredStat));
        when(playerFixtureStatRepository.findTeamHistoryByPlayerIdsAndSeasonOrderByLatest(
                eq(List.of(10L, 20L)),
                eq(2025)
        )).thenReturn(List.of(
                latestTeam(10L, 47L),
                latestTeam(10L, 52L),
                latestTeam(20L, 52L),
                latestTeam(20L, 47L)
        ));

        TeamResponseDto.PlayerRankings rankings = teamPlayerRankingService.getPlayerRankings(47L, 2025);

        assertThat(rankings.getRows())
                .extracting(TeamResponseDto.PlayerRanking::getPlayerId)
                .containsExactly(10L);
    }

    private PlayerTeamSeasonStat seasonStat(Player player, Team team, Integer season,
                                            Integer goals, Integer assists, Integer minutes) {
        return PlayerTeamSeasonStat.builder()
                .player(player)
                .team(team)
                .leagueId(39L)
                .season(season)
                .appearances(10)
                .substitutesBench(0)
                .goals(goals)
                .assists(assists)
                .minutes(minutes)
                .rating(7.1)
                .build();
    }

    private PlayerFixtureStatRepository.LatestPlayerTeam latestTeam(Long playerId, Long teamId) {
        return new PlayerFixtureStatRepository.LatestPlayerTeam() {
            @Override
            public Long getPlayerId() {
                return playerId;
            }

            @Override
            public Long getTeamId() {
                return teamId;
            }
        };
    }
}
