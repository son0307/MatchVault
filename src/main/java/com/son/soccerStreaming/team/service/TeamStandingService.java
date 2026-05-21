package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService.LiveStandingImpact;
import com.son.soccerStreaming.team.dto.TeamStandingResponseDto;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeamStandingService {

    private final TeamStandingRepository teamStandingRepository;
    private final ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;

    public List<TeamStandingResponseDto> getStandings(Integer season) {
        List<StandingProjection> projections = teamStandingRepository.findAllBySeason(season).stream()
                .map(StandingProjection::from)
                .toList();

        applyLiveImpacts(projections, apiFootballStandingLocalUpdateService.findImpacts(season));
        refreshRanks(projections);

        return projections.stream()
                .sorted(Comparator.comparing(StandingProjection::getRank, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();
    }

    private TeamStandingResponseDto toResponse(StandingProjection standing) {
        return TeamStandingResponseDto.builder()
                .season(standing.getSeason())
                .rank(standing.getRank())
                .team(TeamStandingResponseDto.TeamInfo.builder()
                        .id(standing.getTeamId())
                        .name(standing.getTeamName())
                        .logo(standing.getTeamLogoUrl())
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
                .updatedAt(standing.getUpdatedAt())
                .build();
    }

    private void applyLiveImpacts(List<StandingProjection> standings, List<LiveStandingImpact> impacts) {
        Map<Long, StandingProjection> standingsByTeamId = standings.stream()
                .collect(Collectors.toMap(StandingProjection::getTeamId, Function.identity()));

        for (LiveStandingImpact impact : impacts) {
            StandingProjection home = standingsByTeamId.get(impact.getHomeTeamId());
            StandingProjection away = standingsByTeamId.get(impact.getAwayTeamId());
            if (home == null || away == null) {
                continue;
            }

            home.applyMatchResult(true, impact.getHomeScore(), impact.getAwayScore(), impact.getAppliedAt());
            away.applyMatchResult(false, impact.getAwayScore(), impact.getHomeScore(), impact.getAppliedAt());
        }
    }

    private void refreshRanks(List<StandingProjection> standings) {
        List<StandingProjection> sorted = standings.stream()
                .sorted(Comparator
                        .comparing(StandingProjection::getPoints, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StandingProjection::getGoalsDiff, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StandingProjection::getGoalsFor, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StandingProjection::getTeamName))
                .toList();

        for (int index = 0; index < sorted.size(); index++) {
            sorted.get(index).updateRank(index + 1);
        }
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

    @Getter
    private static class StandingProjection {
        private final Integer season;
        private Integer rank;
        private final Long teamId;
        private final String teamName;
        private final String teamLogoUrl;
        private Integer points;
        private Integer goalsDiff;
        private final String group;
        private final String form;
        private final String status;
        private final String description;
        private Integer played;
        private Integer win;
        private Integer draw;
        private Integer lose;
        private Integer goalsFor;
        private Integer goalsAgainst;
        private Integer homePlayed;
        private Integer homeWin;
        private Integer homeDraw;
        private Integer homeLose;
        private Integer homeGoalsFor;
        private Integer homeGoalsAgainst;
        private Integer awayPlayed;
        private Integer awayWin;
        private Integer awayDraw;
        private Integer awayLose;
        private Integer awayGoalsFor;
        private Integer awayGoalsAgainst;
        private LocalDateTime updatedAt;

        private StandingProjection(TeamStanding standing) {
            Team team = standing.getTeam();
            this.season = standing.getSeason();
            this.rank = standing.getRank();
            this.teamId = team.getTeamId();
            this.teamName = team.getName();
            this.teamLogoUrl = team.getLogoUrl();
            this.points = standing.getPoints();
            this.goalsDiff = standing.getGoalsDiff();
            this.group = standing.getGroup();
            this.form = standing.getForm();
            this.status = standing.getStatus();
            this.description = standing.getDescription();
            this.played = standing.getPlayed();
            this.win = standing.getWin();
            this.draw = standing.getDraw();
            this.lose = standing.getLose();
            this.goalsFor = standing.getGoalsFor();
            this.goalsAgainst = standing.getGoalsAgainst();
            this.homePlayed = standing.getHomePlayed();
            this.homeWin = standing.getHomeWin();
            this.homeDraw = standing.getHomeDraw();
            this.homeLose = standing.getHomeLose();
            this.homeGoalsFor = standing.getHomeGoalsFor();
            this.homeGoalsAgainst = standing.getHomeGoalsAgainst();
            this.awayPlayed = standing.getAwayPlayed();
            this.awayWin = standing.getAwayWin();
            this.awayDraw = standing.getAwayDraw();
            this.awayLose = standing.getAwayLose();
            this.awayGoalsFor = standing.getAwayGoalsFor();
            this.awayGoalsAgainst = standing.getAwayGoalsAgainst();
            this.updatedAt = standing.getApiUpdatedAt();
        }

        static StandingProjection from(TeamStanding standing) {
            return new StandingProjection(standing);
        }

        void applyMatchResult(boolean homeSide, int goalsFor, int goalsAgainst, LocalDateTime updatedAt) {
            int pointDelta = pointsFor(goalsFor, goalsAgainst);
            boolean winResult = goalsFor > goalsAgainst;
            boolean drawResult = goalsFor == goalsAgainst;

            this.points = valueOf(this.points) + pointDelta;
            this.goalsDiff = valueOf(this.goalsDiff) + goalsFor - goalsAgainst;
            this.played = valueOf(this.played) + 1;
            this.win = valueOf(this.win) + (winResult ? 1 : 0);
            this.draw = valueOf(this.draw) + (drawResult ? 1 : 0);
            this.lose = valueOf(this.lose) + (!winResult && !drawResult ? 1 : 0);
            this.goalsFor = valueOf(this.goalsFor) + goalsFor;
            this.goalsAgainst = valueOf(this.goalsAgainst) + goalsAgainst;

            if (homeSide) {
                this.homePlayed = valueOf(this.homePlayed) + 1;
                this.homeWin = valueOf(this.homeWin) + (winResult ? 1 : 0);
                this.homeDraw = valueOf(this.homeDraw) + (drawResult ? 1 : 0);
                this.homeLose = valueOf(this.homeLose) + (!winResult && !drawResult ? 1 : 0);
                this.homeGoalsFor = valueOf(this.homeGoalsFor) + goalsFor;
                this.homeGoalsAgainst = valueOf(this.homeGoalsAgainst) + goalsAgainst;
            } else {
                this.awayPlayed = valueOf(this.awayPlayed) + 1;
                this.awayWin = valueOf(this.awayWin) + (winResult ? 1 : 0);
                this.awayDraw = valueOf(this.awayDraw) + (drawResult ? 1 : 0);
                this.awayLose = valueOf(this.awayLose) + (!winResult && !drawResult ? 1 : 0);
                this.awayGoalsFor = valueOf(this.awayGoalsFor) + goalsFor;
                this.awayGoalsAgainst = valueOf(this.awayGoalsAgainst) + goalsAgainst;
            }

            this.updatedAt = updatedAt;
        }

        private int pointsFor(int goalsFor, int goalsAgainst) {
            if (goalsFor > goalsAgainst) {
                return 3;
            }
            if (goalsFor == goalsAgainst) {
                return 1;
            }
            return 0;
        }

        private int valueOf(Integer value) {
            return value == null ? 0 : value;
        }

        private void updateRank(Integer rank) {
            this.rank = rank;
        }
    }
}
