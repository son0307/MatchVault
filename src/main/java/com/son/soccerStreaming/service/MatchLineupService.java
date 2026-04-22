package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchLineupResponseDto;
import com.son.soccerStreaming.entity.MatchLineup;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchLineupRepository;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchLineupService {

    private final MatchLineupRepository matchLineupRepository;
    private final MatchRecordRepository matchRecordRepository;

    public MatchLineupResponseDto.Lineup getMatchLineups(String matchId) {
        // 경기 정보 조회 (홈/원정팀 구분)
        MatchRecord match = matchRecordRepository.findByMatchId(matchId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));

        // 해당 경기의 모든 선수 데이터 조회
        List<MatchLineup> allLineups = matchLineupRepository.findAllByMatchId(matchId);

        // 홈팀과 원정팀으로 선수 구분해서 반환
        return MatchLineupResponseDto.Lineup.builder()
                .matchId(matchId)
                .homeTeam(buildTeamLineup(match.getHomeTeam().getName(), match.getHomeTeam().getId(), allLineups))
                .awayTeam(buildTeamLineup(match.getAwayTeam().getName(), match.getAwayTeam().getId(), allLineups))
                .build();
    }

    private MatchLineupResponseDto.TeamLineup buildTeamLineup(String teamName, Long teamId, List<MatchLineup> lineups) {
        List<MatchLineupResponseDto.PlayerLineup> players = lineups.stream()
                .filter(ml -> ml.getPlayer().getTeam().getId().equals(teamId))
                .map(ml -> MatchLineupResponseDto.PlayerLineup.builder()
                        .playerName(ml.getPlayer().getName())
                        .backNumber(ml.getPlayer().getBackNumber())
                        .formationPosition(ml.getFormationPosition())
                        .isStarting(ml.isStarting())
                        .build())
                .toList();

        return MatchLineupResponseDto.TeamLineup.builder()
                .teamName(teamName)
                .players(players)
                .build();
    }
}
