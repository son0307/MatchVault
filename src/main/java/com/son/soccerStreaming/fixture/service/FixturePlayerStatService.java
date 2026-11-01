package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationReadStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.apifootball.service.FixtureDetailLiveChangeStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailAnonymousPlayerStatReader;
import com.son.soccerStreaming.fixture.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FixturePlayerStatService {

    private final FixtureRepository fixtureRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final FixtureLineupRepository fixtureLineupRepository;
    private final FixtureDetailGenerationResolver generationResolver;
    private final FixtureDetailGenerationReadStore generationReadStore;
    private final FixtureDetailLiveChangeStore liveChangeStore;
    private final FixtureDetailAnonymousPlayerStatReader anonymousPlayerStatReader;

    @Transactional(readOnly = true)
    public FixturePlayerStatResponseDto getFixturePlayerStats(Long fixtureId) {
        Fixture published = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        Long generationId = published.getSeason() == null ? null
                : generationResolver.activeForSeason(published.getSeason());
        Fixture fixture = generationId == null ? published : generationReadStore.findFixture(generationId, fixtureId)
                        .orElseThrow(() -> new IllegalStateException("Published generation is missing fixture " + fixtureId));
        List<PlayerFixtureStat> stats = generationId == null || liveChangeStore.changed(
                published.getSeason(), fixtureId, FixtureDetailLiveChangeStore.Part.PLAYER_STAT)
                ? playerFixtureStatRepository.findAllByFixtureFixtureId(fixtureId)
                : generationReadStore.findPlayerStats(generationId, fixture);
        List<FixtureLineup> lineups = generationId == null || liveChangeStore.changed(
                published.getSeason(), fixtureId, FixtureDetailLiveChangeStore.Part.LINEUP)
                ? fixtureLineupRepository.findAllByFixtureId(fixtureId)
                : generationReadStore.findLineups(generationId, fixture);
        Map<PlayerFixtureKey, Integer> lineupNumbers = lineupNumbersByPlayer(lineups);
        List<FixtureDetailAnonymousPlayerStatReader.AnonymousPlayerStat> anonymousStats =
                generationId == null ? List.of() : anonymousPlayerStatReader.findForFixture(generationId, fixtureId);

        return FixturePlayerStatResponseDto.builder()
                .fixtureId(fixtureId)
                .homeTeam(buildTeamStats(fixture.getHomeTeam(), stats, lineupNumbers, anonymousStats))
                .awayTeam(buildTeamStats(fixture.getAwayTeam(), stats, lineupNumbers, anonymousStats))
                .build();
    }

    private FixturePlayerStatResponseDto.TeamPlayerStats buildTeamStats(
            Team team,
            List<PlayerFixtureStat> allStats,
            Map<PlayerFixtureKey, Integer> lineupNumbers,
            List<FixtureDetailAnonymousPlayerStatReader.AnonymousPlayerStat> anonymousStats
    ) {
        List<FixturePlayerStatResponseDto.PlayerStat> players = Stream.concat(allStats.stream()
                .filter(stat -> stat.getTeam() != null && team.getId().equals(stat.getTeam().getId()))
                .map(stat -> toPlayerStat(stat, lineupNumbers)),
                anonymousStats.stream()
                        .filter(stat -> team.getTeamId().equals(stat.teamId()))
                        .map(FixtureDetailAnonymousPlayerStatReader.AnonymousPlayerStat::stat))
                .sorted(Comparator.comparing(FixturePlayerStatResponseDto.PlayerStat::getJerseyNumber,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();

        return FixturePlayerStatResponseDto.TeamPlayerStats.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
                .players(players)
                .build();
    }

    private FixturePlayerStatResponseDto.PlayerStat toPlayerStat(
            PlayerFixtureStat stat,
            Map<PlayerFixtureKey, Integer> lineupNumbers
    ) {
        return FixturePlayerStatResponseDto.PlayerStat.builder()
                .playerId(stat.getPlayer().getPlayerId())
                .playerName(stat.getPlayer().getName())
                .playerNameKo(stat.getPlayer().getKoreanName())
                .jerseyNumber(jerseyNumberOf(stat, lineupNumbers))
                .position(stat.getPlayer().getPosition())
                .minutesPlayed(valueOfInt(stat.getMinutesPlayed()))
                .rating(valueOfDouble(stat.getRating()))
                .captain(stat.getIsCaptain())
                .substitute(stat.getIsSubstitute())
                .goals(valueOfInt(stat.getGoals()))
                .assists(valueOfInt(stat.getAssists()))
                .conceded(valueOfInt(stat.getConceded()))
                .saves(valueOfInt(stat.getSaves()))
                .shotsTotal(valueOfInt(stat.getShotsTotal()))
                .shotsOnTarget(valueOfInt(stat.getShotsOnTarget()))
                .passesTotal(valueOfInt(stat.getPassesTotal()))
                .passesKey(valueOfInt(stat.getPassesKey()))
                .passesAccurate(valueOfInt(stat.getPassesAccurate()))
                .passAccuracy(valueOfInt(stat.getPassAccuracy()))
                .tacklesTotal(valueOfInt(stat.getTacklesTotal()))
                .blocks(valueOfInt(stat.getBlocks()))
                .interceptions(valueOfInt(stat.getInterceptions()))
                .duelsTotal(valueOfInt(stat.getDuelsTotal()))
                .duelsWon(valueOfInt(stat.getDuelsWon()))
                .dribblesAttempts(valueOfInt(stat.getDribblesAttempts()))
                .dribblesSuccess(valueOfInt(stat.getDribblesSuccess()))
                .dribblesPast(valueOfInt(stat.getDribblesPast()))
                .foulsDrawn(valueOfInt(stat.getFoulsDrawn()))
                .foulsCommitted(valueOfInt(stat.getFoulsCommitted()))
                .yellowCards(valueOfInt(stat.getYellowCards()))
                .redCards(valueOfInt(stat.getRedCards()))
                .offsides(valueOfInt(stat.getOffsides()))
                .penaltyWon(valueOfInt(stat.getPenaltyWon()))
                .penaltyCommitted(valueOfInt(stat.getPenaltyCommitted()))
                .penaltyScored(valueOfInt(stat.getPenaltyScored()))
                .penaltyMissed(valueOfInt(stat.getPenaltyMissed()))
                .penaltySaved(valueOfInt(stat.getPenaltySaved()))
                .build();
    }

    private Map<PlayerFixtureKey, Integer> lineupNumbersByPlayer(List<FixtureLineup> lineups) {
        Map<PlayerFixtureKey, Integer> numbers = new HashMap<>();
        for (FixtureLineup lineup : lineups) {
            if (lineup.getPlayer() == null || lineup.getTeam() == null) {
                continue;
            }
            numbers.put(
                    new PlayerFixtureKey(lineup.getTeam().getTeamId(), lineup.getPlayer().getPlayerId()),
                    lineup.getJerseyNumber()
            );
        }
        return numbers;
    }

    private Integer jerseyNumberOf(PlayerFixtureStat stat, Map<PlayerFixtureKey, Integer> lineupNumbers) {
        PlayerFixtureKey key = new PlayerFixtureKey(stat.getTeam().getTeamId(), stat.getPlayer().getPlayerId());
        return lineupNumbers.getOrDefault(key, stat.getPlayer().getNumber());
    }

    private int valueOfInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double valueOfDouble(Double value) {
        return value == null ? 0 : value;
    }

    private record PlayerFixtureKey(Long teamId, Long playerId) {
    }
}
