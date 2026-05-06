package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballTeamDto;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.entity.Venue;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballTeamSyncService {

    private final ApiFootballClient apiFootballClient;
    private final TeamRepository teamRepository;

    @Transactional
    public int syncTeams(Integer league, Integer season) {
        List<ApiFootballTeamDto.TeamResponse> responses = apiFootballClient.getTeams(league, season);
        int syncedCount = 0;

        for (ApiFootballTeamDto.TeamResponse response : responses) {
            ApiFootballTeamDto.TeamInfo teamInfo = response.getTeam();
            if (teamInfo == null || teamInfo.getId() == null) {
                continue;
            }

            Team team = teamRepository.findByTeamId(teamInfo.getId())
                    .orElseGet(() -> Team.builder()
                            .teamId(teamInfo.getId())
                            .name(teamInfo.getName())
                            .code(teamInfo.getCode())
                            .country(teamInfo.getCountry())
                            .founded(teamInfo.getFounded())
                            .logoUrl(teamInfo.getLogo())
                            .build());

            team.updateTeam(
                    teamInfo.getName(),
                    teamInfo.getCode(),
                    teamInfo.getCountry(),
                    teamInfo.getFounded(),
                    teamInfo.getLogo()
            );
            upsertVenue(team, response.getVenue());

            teamRepository.save(team);
            syncedCount++;
        }

        log.info("API-Football team sync completed. league={}, season={}, count={}", league, season, syncedCount);
        return syncedCount;
    }

    private void upsertVenue(Team team, ApiFootballTeamDto.VenueInfo venueInfo) {
        if (venueInfo == null || venueInfo.getId() == null) {
            return;
        }

        Venue venue = team.getVenue();
        if (venue == null || !venueInfo.getId().equals(venue.getVenueId())) {
            venue = Venue.builder()
                    .venueId(venueInfo.getId())
                    .build();
        }

        venue.updateVenue(
                venueInfo.getName(),
                venueInfo.getAddress(),
                venueInfo.getCity(),
                venueInfo.getCapacity(),
                venueInfo.getSurface(),
                venueInfo.getImage()
        );
        team.updateVenue(venue);
    }
}
