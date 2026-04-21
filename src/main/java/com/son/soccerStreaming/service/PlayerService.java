package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;

    public List<PlayerResponseDto.Summary> getPlayersByTeam(String teamId) {
        return playerRepository.findAllByTeamTeamId(teamId).stream()
                .map(player -> PlayerResponseDto.Summary.builder()
                        .playerId(player.getPlayerId())
                        .name(player.getName())
                        .backNumber(player.getBackNumber()).build()).toList();
    }

    public PlayerResponseDto.Details getPlayerDetails(String playerId) {
        Player playerData = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        return PlayerResponseDto.Details.builder()
                .playerId(playerData.getPlayerId())
                .name(playerData.getName())
                .backNumber(playerData.getBackNumber())
                .age(playerData.getAge())
                .height(playerData.getHeight())
                .weight(playerData.getWeight())
                .mainPosition(playerData.getMainPosition())
                .subPosition(playerData.getSubPosition())
                .build();
    }
}
