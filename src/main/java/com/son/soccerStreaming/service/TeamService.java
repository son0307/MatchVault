package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.dto.TeamResponseDto;
import com.son.soccerStreaming.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.entity.Venue;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;

    public List<TeamResponseDto.Summary> getTeams() {
        return teamRepository.findAllByOrderByNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    public TeamResponseDto.Details getTeamDetails(Long teamId) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        return TeamResponseDto.Details.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .code(team.getCode())
                .country(team.getCountry())
                .founded(team.getFounded())
                .logoUrl(team.getLogoUrl())
                .venue(toVenueInfo(team.getVenue()))
                .build();
    }

    public List<PlayerResponseDto.Summary> getPlayersByTeam(Long teamId, Integer season) {
        List<PlayerTeamSeasonStat> candidates = playerTeamSeasonStatRepository.findAllByTeamAndSeason(teamId, season);
        Map<Long, Long> latestTeamByPlayer = latestTeamByPlayer(candidates, season);

        // 경기 출전 기록이 있으면 최신 출전 팀 기준으로 이적 선수를 걸러내고, 기록이 없으면 시즌 등록 정보를 따른다.
        return candidates.stream()
                .filter(stat -> belongsToTeamByLatestMatch(stat, teamId, latestTeamByPlayer))
                .map(this::toPlayerSummary)
                .toList();
    }

    private Map<Long, Long> latestTeamByPlayer(List<PlayerTeamSeasonStat> candidates, Integer season) {
        if (candidates.isEmpty()) {
            return Map.of();
        }

        List<Long> playerIds = candidates.stream()
                .map(stat -> stat.getPlayer().getPlayerId())
                .toList();

        return playerFixtureStatRepository.findLatestTeamsByPlayerIdsAndSeason(playerIds, season).stream()
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

    private PlayerResponseDto.Summary toPlayerSummary(PlayerTeamSeasonStat stat) {
        return PlayerResponseDto.Summary.builder()
                .playerId(stat.getPlayer().getPlayerId())
                .playerName(stat.getPlayer().getName())
                .backNumber(stat.getBackNumber())
                .position(stat.getPosition() != null ? stat.getPosition() : stat.getPlayer().getPosition())
                .photoUrl(stat.getPlayer().getPhotoUrl())
                .build();
    }

    private TeamResponseDto.Summary toSummary(Team team) {
        return TeamResponseDto.Summary.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .code(team.getCode())
                .logoUrl(team.getLogoUrl())
                .build();
    }

    private TeamResponseDto.VenueInfo toVenueInfo(Venue venue) {
        if (venue == null) {
            return null;
        }

        return TeamResponseDto.VenueInfo.builder()
                .venueId(venue.getVenueId())
                .venueName(venue.getVenueName())
                .venueAddress(venue.getVenueAddress())
                .venueCity(venue.getVenueCity())
                .capacity(venue.getCapacity())
                .surface(venue.getSurface())
                .venueImageUrl(venue.getVenueImageUrl())
                .build();
    }
}
