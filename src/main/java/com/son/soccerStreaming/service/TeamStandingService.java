package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.TeamStandingResponseDto;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.entity.TeamStanding;
import com.son.soccerStreaming.repository.TeamStandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamStandingService {

    private final TeamStandingRepository teamStandingRepository;

    public List<TeamStandingResponseDto> getStandings(Integer season) {
        return teamStandingRepository.findAllBySeasonOrderByRankAsc(season).stream()
                .map(this::toResponse)
                .toList();
    }

    private TeamStandingResponseDto toResponse(TeamStanding standing) {
        Team team = standing.getTeam();

        return TeamStandingResponseDto.builder()
                .season(standing.getSeason())
                .rank(standing.getRank())
                .team(TeamStandingResponseDto.TeamInfo.builder()
                        .id(team.getTeamId())
                        .name(team.getName())
                        .logo(team.getLogoUrl())
                        .build())
                .points(standing.getPoints())
                .goalsDiff(standing.getGoalsDiff())
                .group(standing.getGroup())
                .form(standing.getForm())
                .status(standing.getStatus())
                .description(standing.getDescription())
                .all(toRecord(
                        standing.getPlayed(),
                        standing.getWin(),
                        standing.getDraw(),
                        standing.getLose(),
                        standing.getGoalsFor(),
                        standing.getGoalsAgainst()
                ))
                .home(toRecord(
                        standing.getHomePlayed(),
                        standing.getHomeWin(),
                        standing.getHomeDraw(),
                        standing.getHomeLose(),
                        standing.getHomeGoalsFor(),
                        standing.getHomeGoalsAgainst()
                ))
                .away(toRecord(
                        standing.getAwayPlayed(),
                        standing.getAwayWin(),
                        standing.getAwayDraw(),
                        standing.getAwayLose(),
                        standing.getAwayGoalsFor(),
                        standing.getAwayGoalsAgainst()
                ))
                .updatedAt(standing.getApiUpdatedAt())
                .build();
    }

    private TeamStandingResponseDto.Record toRecord(Integer played, Integer win, Integer draw, Integer lose,
                                                    Integer goalsFor, Integer goalsAgainst) {
        return TeamStandingResponseDto.Record.builder()
                .played(played)
                .win(win)
                .draw(draw)
                .lose(lose)
                .goals(TeamStandingResponseDto.Goals.builder()
                        .goalsFor(goalsFor)
                        .goalsAgainst(goalsAgainst)
                        .build())
                .build();
    }
}
