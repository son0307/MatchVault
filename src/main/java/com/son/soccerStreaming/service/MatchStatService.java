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
import java.util.OptionalDouble;

@Service
@RequiredArgsConstructor
public class MatchStatService {

    private final MatchRecordRepository matchRecordRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;

    @Transactional(readOnly = true)
    // 💡 String matchId -> Long fixtureId 로 변경
    public MatchStatResponseDto getMatchStats(Long fixtureId) {

        MatchRecord matchRecord = matchRecordRepository.findByApiFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));

        // 💡 JPA 메서드명 변경 적용 (MatchId -> ApiFixtureId)
        List<PlayerMatchStat> allStats = playerMatchStatRepository.findAllByMatchRecordApiFixtureId(fixtureId);

        return MatchStatResponseDto.builder()
                .matchId(fixtureId) // DTO 내부 타입도 Long이어야 합니다.
                .homeTeamStat(aggregateTeamStats(matchRecord.getHomeTeam().getTeamApiId(), matchRecord.getHomeScore(), allStats))
                .awayTeamStat(aggregateTeamStats(matchRecord.getAwayTeam().getTeamApiId(), matchRecord.getAwayScore(), allStats))
                .build();
    }

    private MatchStatResponseDto.TeamStatSummary aggregateTeamStats(Long teamId, int score, List<PlayerMatchStat> matchStats) {
        // 💡 s.getPlayer().getTeam() 이 아닌 s.getTeam() 으로 바로 접근 (성능 최적화)
        List<PlayerMatchStat> teamStats = matchStats.stream()
                .filter(s -> s.getTeam().getTeamApiId().equals(teamId))
                .toList();

        // 💡 새 엔티티 필드명에 맞춘 스트림 집계
        int totalShots = teamStats.stream().mapToInt(PlayerMatchStat::getShotsTotal).sum();
        int totalShotsOnTarget = teamStats.stream().mapToInt(PlayerMatchStat::getShotsOnTarget).sum();
        int totalPasses = teamStats.stream().mapToInt(PlayerMatchStat::getPassesTotal).sum();
        int totalFouls = teamStats.stream().mapToInt(PlayerMatchStat::getFoulsCommitted).sum();
        int totalTackles = teamStats.stream().mapToInt(PlayerMatchStat::getTacklesTotal).sum();

        // 💡 성공한 패스 개수 대신, 평균 패스 정확도(%)로 변경됨
        OptionalDouble avgPassAccuracy = teamStats.stream()
                .filter(s -> s.getPassAccuracy() != null)
                .mapToDouble(PlayerMatchStat::getPassAccuracy)
                .average();

        int yellowCards = teamStats.stream().mapToInt(PlayerMatchStat::getYellowCards).sum();
        int redCards = teamStats.stream().mapToInt(PlayerMatchStat::getRedCards).sum();

        return MatchStatResponseDto.TeamStatSummary.builder()
                .teamId(teamId)
                .score(score)
                .yellowCards(yellowCards)
                .redCards(redCards)
                .build();
    }
}