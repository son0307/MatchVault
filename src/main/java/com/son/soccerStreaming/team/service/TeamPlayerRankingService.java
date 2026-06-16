package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.dto.TeamResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamPlayerRankingService {

    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final MediaUrlService mediaUrlService;

    @Cacheable(
            cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
            key = "'team:' + #teamId + ':season:' + #season",
            sync = true
    )
    public TeamResponseDto.PlayerRankings getPlayerRankings(Long teamId, Integer season) {
        List<PlayerTeamSeasonStat> candidates = playerTeamSeasonStatRepository.findAllByTeamAndSeason(teamId, season);
        Map<Long, Long> latestTeamByPlayer = latestTeamByPlayer(candidates, season);
        List<TeamResponseDto.PlayerRanking> rows = candidates.stream()
                .filter(stat -> belongsToTeamByLatestMatch(stat, teamId, latestTeamByPlayer))
                .map(this::toPlayerRanking)
                .collect(Collectors.toCollection(ArrayList::new));

        return TeamResponseDto.PlayerRankings.builder()
                .rows(rows)
                .build();
    }

    private Map<Long, Long> latestTeamByPlayer(List<PlayerTeamSeasonStat> candidates, Integer season) {
        if (candidates.isEmpty()) {
            return Map.of();
        }

        List<Long> playerIds = candidates.stream()
                .map(stat -> stat.getPlayer().getPlayerId())
                .distinct()
                .toList();

        return playerFixtureStatRepository.findTeamHistoryByPlayerIdsAndSeasonOrderByLatest(playerIds, season).stream()
                .collect(Collectors.toMap(
                        PlayerFixtureStatRepository.LatestPlayerTeam::getPlayerId,
                        PlayerFixtureStatRepository.LatestPlayerTeam::getTeamId,
                        (existingTeamId, duplicateTeamId) -> existingTeamId
                ));
    }

    private boolean belongsToTeamByLatestMatch(
            PlayerTeamSeasonStat stat,
            Long teamId,
            Map<Long, Long> latestTeamByPlayer
    ) {
        Long latestTeamId = latestTeamByPlayer.get(stat.getPlayer().getPlayerId());
        return latestTeamId == null || teamId.equals(latestTeamId);
    }

    private TeamResponseDto.PlayerRanking toPlayerRanking(PlayerTeamSeasonStat stat) {
        return TeamResponseDto.PlayerRanking.builder()
                .playerId(stat.getPlayer().getPlayerId())
                .playerName(stat.getPlayer().getName())
                .photoUrl(mediaUrlService.playerPhotoUrl(stat.getPlayer()))
                .position(stat.getPosition() != null ? stat.getPosition() : stat.getPlayer().getPosition())
                .goals(valueOf(stat.getGoals()))
                .assists(valueOf(stat.getAssists()))
                .rating(stat.getRating() != null ? roundToOneDecimal(stat.getRating()) : 0)
                .minutes(valueOf(stat.getMinutes()))
                .build();
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
