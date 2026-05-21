package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.fixture.dto.FixtureStatResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureStat;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import com.son.soccerStreaming.fixture.repository.FixtureStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FixtureStatService {

    private final FixtureRecordRepository fixtureRecordRepository;
    private final FixtureStatRepository fixtureStatRepository;

    @Transactional(readOnly = true)
    public FixtureStatResponseDto getFixtureStats(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        Map<Long, FixtureStat> statsByTeamId = fixtureStatRepository.findAllByFixtureFixtureId(fixtureId).stream()
                .filter(stat -> stat.getTeam() != null)
                .collect(Collectors.toMap(stat -> stat.getTeam().getTeamId(), Function.identity()));

        return FixtureStatResponseDto.builder()
                .fixtureId(fixtureId)
                .homeTeamStat(toSummary(fixture.getHomeTeam().getTeamId(), valueOf(fixture.getHomeScore()), statsByTeamId))
                .awayTeamStat(toSummary(fixture.getAwayTeam().getTeamId(), valueOf(fixture.getAwayScore()), statsByTeamId))
                .build();
    }

    public FixtureStatResponseDto.TeamStatSummary getTeamStatSummary(Long fixtureId, Long teamId, int score) {
        return fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(fixtureId, teamId)
                .map(stat -> toSummary(teamId, score, stat))
                .orElseGet(() -> emptySummary(teamId, score));
    }

    private FixtureStatResponseDto.TeamStatSummary toSummary(Long teamId, int score, Map<Long, FixtureStat> statsByTeamId) {
        FixtureStat stat = statsByTeamId.get(teamId);
        return stat == null ? emptySummary(teamId, score) : toSummary(teamId, score, stat);
    }

    private FixtureStatResponseDto.TeamStatSummary toSummary(Long teamId, int score, FixtureStat stat) {
        return FixtureStatResponseDto.TeamStatSummary.builder()
                .teamId(teamId)
                .score(score)
                .shotsOnGoal(valueOf(stat.getShotsOnGoal()))
                .shotsOffGoal(valueOf(stat.getShotsOffGoal()))
                .totalShots(valueOf(stat.getTotalShots()))
                .shotsOnTarget(valueOf(stat.getShotsOnGoal()))
                .blockedShots(valueOf(stat.getBlockedShots()))
                .shotsInsideBox(valueOf(stat.getShotsInsideBox()))
                .shotsOutsideBox(valueOf(stat.getShotsOutsideBox()))
                .totalPasses(valueOf(stat.getTotalPasses()))
                .passesAccurate(valueOf(stat.getPassesAccurate()))
                .passAccuracy(valueOf(stat.getPassAccuracy()))
                .fouls(valueOf(stat.getFouls()))
                .cornerKicks(valueOf(stat.getCornerKicks()))
                .offsides(valueOf(stat.getOffsides()))
                .ballPossession(valueOf(stat.getBallPossession()))
                .goalkeeperSaves(valueOf(stat.getGoalkeeperSaves()))
                .yellowCards(valueOf(stat.getYellowCards()))
                .redCards(valueOf(stat.getRedCards()))
                .expectedGoals(stat.getExpectedGoals())
                .build();
    }

    private FixtureStatResponseDto.TeamStatSummary emptySummary(Long teamId, int score) {
        return FixtureStatResponseDto.TeamStatSummary.builder()
                .teamId(teamId)
                .score(score)
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
