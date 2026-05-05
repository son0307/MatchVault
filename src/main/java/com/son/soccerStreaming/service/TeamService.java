package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.dto.TeamResponseDto;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.entity.Venue;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.PlayerRepository;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

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

    public List<PlayerResponseDto.Summary> getPlayersByTeam(Long teamId) {
        return playerRepository.findAllByTeamTeamId(teamId).stream()
                .map(player -> PlayerResponseDto.Summary.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getName())
                        .backNumber(player.getDefaultNumber())
                        .position(player.getPosition())
                        .photoUrl(player.getPhotoUrl())
                        .build())
                .toList();
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
