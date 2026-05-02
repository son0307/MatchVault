package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.PlayerMatchStatRepository;
import com.son.soccerStreaming.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;

    @Transactional(readOnly = true)
    public PlayerResponseDto.Details getPlayerDetails(Long playerId) {
        Player playerData = playerRepository.findByApiPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        return PlayerResponseDto.Details.builder()
                .playerId(playerData.getApiPlayerId())
                .playerName(playerData.getName())
                .backNumber(playerData.getDefaultNumber())
                .age(playerData.getAge())
                // 💡 단위를 제거하고 숫자만 파싱하는 안전한 헬퍼 메서드 사용
                .height(parsePhysicalStat(playerData.getHeight()))
                .weight(parsePhysicalStat(playerData.getWeight()))
                .position(playerData.getPosition())
                .build();
    }

    @Cacheable(value = "playerStats", key = "#playerId")
    @Transactional(readOnly = true)
    public PlayerResponseDto.SeasonStats getPlayerSeasonStats(Long playerId) {

        Player player = playerRepository.findByApiPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        // DB에서 최신 스탯 통계 가져오기
        PlayerMatchStatRepository.SeasonStatSummary stats =
                playerMatchStatRepository.findSeasonStatSummaryByPlayerId(playerId);

        // 💡 DTO 조립 (변경된 레포지토리 규격 완벽 반영)
        return PlayerResponseDto.SeasonStats.builder()
                .playerId(player.getApiPlayerId())
                .totalMatches(stats.getTotalMatches())
                .goals(stats.getGoals())
                .assists(stats.getAssists())
                .shots(stats.getShotsTotal())        // 변경됨: getShots -> getShotsTotal
                .shotsOnTarget(stats.getShotsOnTarget())
                .totalPasses(stats.getPassesTotal()) // 변경됨: getTotalPasses -> getPassesTotal
                // 💡 레포지토리에서 바로 평균값을 계산해오므로 별도 수동 계산 로직 삭제
                // 소수점 첫째 자리까지만 깔끔하게 떨어지도록 반올림
                .passAccuracy(Math.round(stats.getAvgPassAccuracy() * 10) / 10.0)
                .fouls(stats.getFoulsCommitted())    // 변경됨: getFouls -> getFoulsCommitted
                .tackles(stats.getTacklesTotal())    // 변경됨: getTackles -> getTacklesTotal
                // (선택) DTO 쪽에 필드를 추가하셨다면 아래 항목도 추가 가능
                // .yellowCards(stats.getYellowCards())
                // .redCards(stats.getRedCards())
                .build();
    }

    // 💡 API-Sports 데이터가 없거나 형식이 다를 때 에러가 터지는 것을 막는 헬퍼 메서드
    private Integer parsePhysicalStat(String statStr) {
        if (statStr == null || statStr.isBlank()) {
            return 0; // 데이터가 없으면 0 반환
        }
        try {
            return Integer.parseInt(statStr.split(" ")[0]);
        } catch (NumberFormatException e) {
            return 0; // "Unknown cm" 같은 예외 데이터 방어
        }
    }
}