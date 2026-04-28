package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchStatResponseDto;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.entity.PlayerMatchStat;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import com.son.soccerStreaming.repository.PlayerMatchStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchStatService {

    private final MatchRecordRepository matchRecordRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;

    @Transactional(readOnly = true)
    public MatchStatResponseDto getMatchStats(String matchId) {
        // 경기 정보 조회
        MatchRecord matchRecord = matchRecordRepository.findByMatchId(matchId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));

        // 해당 경기의 모든 선수 스탯 조회
        List<PlayerMatchStat> allStats = playerMatchStatRepository.findAllByMatchRecordMatchId(matchId);

        // 팀별 집계 및 DTO 반환
        return MatchStatResponseDto.builder()
                .matchId(matchId)
                .homeTeam(aggregateTeamStats(matchRecord.getHomeTeam().getName(), matchRecord.getHomeTeam().getTeamId(), matchRecord.getHomeScore(), allStats))
                .awayTeam(aggregateTeamStats(matchRecord.getAwayTeam().getName(), matchRecord.getAwayTeam().getTeamId(), matchRecord.getAwayScore(), allStats))
                .build();
    }

    private MatchStatResponseDto.TeamStatSummary aggregateTeamStats(String teamName, String teamId, int score, List<PlayerMatchStat> matchStats) {
        // 해당 팀 선수의 스탯만 필터링
        List<PlayerMatchStat> teamStats = matchStats.stream()
                .filter(s -> s.getPlayer().getTeam().getTeamId().equals(teamId))
                .toList();

        int totalShots = teamStats.stream().mapToInt(PlayerMatchStat::getShots).sum();
        int totalShotsOnTarget = teamStats.stream().mapToInt(PlayerMatchStat::getShotsOnTarget).sum();
        int totalPasses = teamStats.stream().mapToInt(PlayerMatchStat::getTotalPasses).sum();
        int totalSuccessPasses = teamStats.stream().mapToInt(PlayerMatchStat::getSuccessfulPasses).sum();
        int totalFouls = teamStats.stream().mapToInt(PlayerMatchStat::getFouls).sum();
        int totalTackles = teamStats.stream().mapToInt(PlayerMatchStat::getTackles).sum();

        return MatchStatResponseDto.TeamStatSummary.builder()
                .teamName(teamName)
                .score(score)
                .totalShots(totalShots)
                .shotsOnTarget(totalShotsOnTarget)
                .passes(totalPasses)
                .passAccuracy(totalPasses > 0 ? (totalSuccessPasses * 100 / totalPasses) : 0)
                .fouls(totalFouls)
                .tackles(totalTackles)
                .build();
    }
}
