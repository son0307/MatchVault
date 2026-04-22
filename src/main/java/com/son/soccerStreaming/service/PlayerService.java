package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.PlayerMatchStatRepository;
import com.son.soccerStreaming.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;

    public PlayerResponseDto.Details getPlayerDetails(String playerId) {
        Player playerData = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        return PlayerResponseDto.Details.builder()
                .playerId(playerData.getPlayerId())
                .playerName(playerData.getName())
                .backNumber(playerData.getBackNumber())
                .age(playerData.getAge())
                .height(playerData.getHeight())
                .weight(playerData.getWeight())
                .mainPosition(playerData.getMainPosition())
                .subPosition(playerData.getSubPosition())
                .build();
    }

    public PlayerResponseDto.SeasonStats getPlayerSeasonStats(String playerId) {
        // 선수가 실제로 존재하는지 확인
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        // DB에서 스탯 통계 가져오기
        PlayerMatchStatRepository.SeasonStatSummary stats =
                playerMatchStatRepository.findSeasonStatSummaryByPlayerId(playerId);

        // 패스 성공률 계산
        double accuracy = stats.getSuccessfulPasses() > 0
                ? Math.round(stats.getSuccessfulPasses() * 100.0 / stats.getTotalPasses()) : 0;

        // DTO 조립 및 반환
        return PlayerResponseDto.SeasonStats.builder()
                .playerId(playerId)
                .totalMatches(stats.getTotalMatches())
                .goals(stats.getGoals())
                .assists(stats.getAssists())
                .shots(stats.getShots())
                .shotsOnTarget(stats.getShotsOnTarget())
                .totalPasses(stats.getTotalPasses())
                .passAccuracy(accuracy)
                .fouls(stats.getFouls())
                .tackles(stats.getTackles())
                .build();
    }
}
