package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.repository.PlayerRepository;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    public List<PlayerResponseDto.Summary> getPlayersByTeam(String teamId) {
        return playerRepository.findAllByTeamTeamId(teamId).stream()
                .map(player -> PlayerResponseDto.Summary.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getName())
                        .backNumber(player.getBackNumber()).build()).toList();
    }
}
