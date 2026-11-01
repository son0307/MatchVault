package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationReadStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.apifootball.service.FixtureDetailLiveChangeStore;
import com.son.soccerStreaming.fixture.dto.FixtureStatResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureStat;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.FixtureStatRepository;
import com.son.soccerStreaming.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FixtureStatService {

    private final FixtureRepository fixtureRepository;
    private final FixtureStatRepository fixtureStatRepository;
    private final FixtureDetailGenerationResolver generationResolver;
    private final FixtureDetailGenerationReadStore generationReadStore;
    private final FixtureDetailLiveChangeStore liveChangeStore;

    @Transactional(readOnly = true)
    public FixtureStatResponseDto getFixtureStats(Long fixtureId) {
        Fixture published = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        Long generationId = published.getSeason() == null ? null
                : generationResolver.activeForSeason(published.getSeason());
        Fixture fixture = generationId == null ? published : generationReadStore.findFixture(generationId, fixtureId)
                        .orElseThrow(() -> new IllegalStateException("Published generation is missing fixture " + fixtureId));

        Map<Long, FixtureStat> statsByTeamId = (generationId == null || liveChangeStore.changed(
                published.getSeason(), fixtureId, FixtureDetailLiveChangeStore.Part.TEAM_STAT)
                ? fixtureStatRepository.findAllByFixtureFixtureId(fixtureId)
                : generationReadStore.findTeamStats(generationId, fixture)).stream()
                .filter(stat -> stat.getTeam() != null)
                .collect(Collectors.toMap(stat -> stat.getTeam().getTeamId(), Function.identity()));

        return FixtureStatResponseDto.builder()
                .fixtureId(fixtureId)
                .homeTeamStat(toSummary(fixture.getHomeTeam(), valueOf(fixture.getHomeScore()), statsByTeamId))
                .awayTeamStat(toSummary(fixture.getAwayTeam(), valueOf(fixture.getAwayScore()), statsByTeamId))
                .build();
    }

    public FixtureStatResponseDto.TeamStatSummary getTeamStatSummary(Long fixtureId, Long teamId, int score) {
        if (!generationResolver.enabled()) {
            return fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(fixtureId, teamId)
                    .map(stat -> toSummary(teamId, score, stat))
                    .orElseGet(() -> emptySummary(teamId, score));
        }
        Fixture published = fixtureRepository.findByFixtureId(fixtureId).orElse(null);
        Long generationId = published == null || published.getSeason() == null ? null
                : generationResolver.activeForSeason(published.getSeason());
        if (generationId != null && !liveChangeStore.changed(
                published.getSeason(), fixtureId, FixtureDetailLiveChangeStore.Part.TEAM_STAT)) {
            Fixture fixture = generationReadStore.findFixture(generationId, fixtureId)
                    .orElseThrow(() -> new IllegalStateException("Published generation is missing fixture " + fixtureId));
            return generationReadStore.findTeamStats(generationId, fixture).stream()
                    .filter(stat -> stat.getTeam().getTeamId().equals(teamId))
                    .findFirst()
                    .map(stat -> toSummary(teamId, score, stat))
                    .orElseGet(() -> emptySummary(teamId, score));
        }
        return fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(fixtureId, teamId)
                .map(stat -> toSummary(teamId, score, stat))
                .orElseGet(() -> emptySummary(teamId, score));
    }

    private FixtureStatResponseDto.TeamStatSummary toSummary(Long teamId, int score, Map<Long, FixtureStat> statsByTeamId) {
        FixtureStat stat = statsByTeamId.get(teamId);
        return stat == null ? emptySummary(teamId, score) : toSummary(teamId, score, stat);
    }

    private FixtureStatResponseDto.TeamStatSummary toSummary(Team team, int score, Map<Long, FixtureStat> statsByTeamId) {
        FixtureStat stat = statsByTeamId.get(team.getTeamId());
        return stat == null ? emptySummary(team, score) : toSummary(team, score, stat);
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

    private FixtureStatResponseDto.TeamStatSummary toSummary(Team team, int score, FixtureStat stat) {
        return FixtureStatResponseDto.TeamStatSummary.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
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

    private FixtureStatResponseDto.TeamStatSummary emptySummary(Team team, int score) {
        return FixtureStatResponseDto.TeamStatSummary.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
                .score(score)
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
