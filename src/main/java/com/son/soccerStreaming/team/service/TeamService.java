package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.player.dto.PlayerResponseDto;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.team.dto.TeamResponseDto;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final MediaUrlService mediaUrlService;
    private final TeamPlayerRankingService teamPlayerRankingService;

    public List<TeamResponseDto.Summary> getTeams() {
        return teamRepository.findAllByOrderByNameAsc().stream()
                .map(this::toSummary)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public TeamResponseDto.Details getTeamDetails(Long teamId) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        return TeamResponseDto.Details.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
                .code(team.getCode())
                .country(team.getCountry())
                .founded(team.getFounded())
                .logoUrl(mediaUrlService.teamLogoUrl(team))
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
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public TeamResponseDto.PlayerRankings getPlayerRankings(Long teamId, Integer season) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        return teamPlayerRankingService.getPlayerRankings(team.getTeamId(), season);
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
                        item -> item,
                        this::latestRecord
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getTeamId()
                ));
    }

    private PlayerFixtureStatRepository.LatestPlayerTeam latestRecord(
            PlayerFixtureStatRepository.LatestPlayerTeam left,
            PlayerFixtureStatRepository.LatestPlayerTeam right
    ) {
        int dateCompare = compareNullable(left.getFixtureDate(), right.getFixtureDate());
        if (dateCompare != 0) {
            return dateCompare >= 0 ? left : right;
        }
        return compareNullable(left.getFixtureId(), right.getFixtureId()) >= 0 ? left : right;
    }

    private <T extends Comparable<T>> int compareNullable(T left, T right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
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
                .playerNameKo(stat.getPlayer().getKoreanName())
                .backNumber(stat.getBackNumber())
                .position(stat.getPosition() != null ? stat.getPosition() : stat.getPlayer().getPosition())
                .photoUrl(mediaUrlService.playerPhotoUrl(stat.getPlayer()))
                .build();
    }

    private TeamResponseDto.Summary toSummary(Team team) {
        return TeamResponseDto.Summary.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
                .code(team.getCode())
                .logoUrl(mediaUrlService.teamLogoUrl(team))
                .build();
    }

    private TeamResponseDto.VenueInfo toVenueInfo(Venue venue) {
        if (venue == null) {
            return null;
        }

        return TeamResponseDto.VenueInfo.builder()
                .venueId(venue.getVenueId())
                .venueName(venue.getVenueName())
                .venueNameKo(venue.getVenueNameKo())
                .venueAddress(venue.getVenueAddress())
                .venueCity(venue.getVenueCity())
                .capacity(venue.getCapacity())
                .surface(venue.getSurface())
                .venueImageUrl(mediaUrlService.venueImageUrl(venue))
                .build();
    }

}
