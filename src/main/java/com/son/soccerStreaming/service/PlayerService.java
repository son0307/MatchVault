package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.Team;
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
        Player player = playerRepository.findByApiPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        Team team = player.getTeam();

        return PlayerResponseDto.Details.builder()
                .playerId(player.getApiPlayerId())
                .playerName(player.getName())
                .firstname(player.getFirstname())
                .lastname(player.getLastname())
                .backNumber(player.getDefaultNumber())
                .age(player.getAge())
                .birthDate(player.getBirthDate())
                .birthPlace(player.getBirthPlace())
                .birthCountry(player.getBirthCountry())
                .nationality(player.getNationality())
                .height(player.getHeight())
                .weight(player.getWeight())
                .position(player.getPosition())
                .photoUrl(player.getPhotoUrl())
                .teamId(team != null ? team.getTeamApiId() : null)
                .teamName(team != null ? team.getName() : null)
                .teamLogoUrl(team != null ? team.getLogoUrl() : null)
                .build();
    }

    @Cacheable(value = "playerStats", key = "#playerId")
    @Transactional(readOnly = true)
    public PlayerResponseDto.SeasonStats getPlayerSeasonStats(Long playerId) {
        Player player = playerRepository.findByApiPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        PlayerMatchStatRepository.SeasonStatSummary stats =
                playerMatchStatRepository.findSeasonStatSummaryByPlayerId(playerId);

        return PlayerResponseDto.SeasonStats.builder()
                .playerId(player.getApiPlayerId())
                .totalMatches(stats.getTotalMatches())
                .minutesPlayed(stats.getMinutesPlayed())
                .averageRating(roundToOneDecimal(stats.getAverageRating()))
                .goals(stats.getGoals())
                .assists(stats.getAssists())
                .conceded(stats.getConceded())
                .saves(stats.getSaves())
                .shots(stats.getShotsTotal())
                .shotsOnTarget(stats.getShotsOnTarget())
                .totalPasses(stats.getPassesTotal())
                .keyPasses(stats.getPassesKey())
                .passAccuracy(roundToOneDecimal(stats.getAvgPassAccuracy()))
                .foulsDrawn(stats.getFoulsDrawn())
                .foulsCommitted(stats.getFoulsCommitted())
                .tackles(stats.getTacklesTotal())
                .blocks(stats.getBlocks())
                .interceptions(stats.getInterceptions())
                .duelsTotal(stats.getDuelsTotal())
                .duelsWon(stats.getDuelsWon())
                .dribblesAttempts(stats.getDribblesAttempts())
                .dribblesSuccess(stats.getDribblesSuccess())
                .dribblesPast(stats.getDribblesPast())
                .yellowCards(stats.getYellowCards())
                .redCards(stats.getRedCards())
                .offsides(stats.getOffsides())
                .penaltyWon(stats.getPenaltyWon())
                .penaltyCommitted(stats.getPenaltyCommitted())
                .penaltyScored(stats.getPenaltyScored())
                .penaltyMissed(stats.getPenaltyMissed())
                .penaltySaved(stats.getPenaltySaved())
                .build();
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
