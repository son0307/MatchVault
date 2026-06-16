package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.media.service.MediaUrlService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
}
