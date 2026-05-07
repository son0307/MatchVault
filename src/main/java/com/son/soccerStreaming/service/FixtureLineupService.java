package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.FixtureLineupResponseDto;
import com.son.soccerStreaming.entity.FixtureLineup;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.PlayerAbsence;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.FixtureLineupRepository;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import com.son.soccerStreaming.repository.PlayerAbsenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FixtureLineupService {

    private final FixtureLineupRepository fixtureLineupRepository;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final PlayerAbsenceRepository playerAbsenceRepository;

    public FixtureLineupResponseDto.Lineup getFixtureLineups(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        List<FixtureLineup> lineups = fixtureLineupRepository.findAllByFixtureId(fixtureId);
        List<PlayerAbsence> absences = playerAbsenceRepository.findAllByFixtureFixtureId(fixtureId);

        return FixtureLineupResponseDto.Lineup.builder()
                .fixtureId(fixtureId)
                .homeTeam(buildTeamLineup(
                        fixture,
                        fixture.getHomeTeam(),
                        fixture.getHomeFormation(),
                        fixture.getHomeCoachName(),
                        lineups,
                        absences
                ))
                .awayTeam(buildTeamLineup(
                        fixture,
                        fixture.getAwayTeam(),
                        fixture.getAwayFormation(),
                        fixture.getAwayCoachName(),
                        lineups,
                        absences
                ))
                .build();
    }

    private FixtureLineupResponseDto.TeamLineup buildTeamLineup(
            Fixture fixture,
            Team team,
            String formation,
            String coachName,
            List<FixtureLineup> lineups,
            List<PlayerAbsence> absences
    ) {
        List<FixtureLineupResponseDto.PlayerLineup> starters = lineups.stream()
                .filter(lineup -> team.getId().equals(lineup.getTeam().getId()))
                .filter(FixtureLineup::isStarter)
                .map(this::toPlayerLineup)
                .toList();

        List<FixtureLineupResponseDto.PlayerLineup> substitutes = lineups.stream()
                .filter(lineup -> team.getId().equals(lineup.getTeam().getId()))
                .filter(lineup -> !lineup.isStarter())
                .map(this::toPlayerLineup)
                .toList();

        List<FixtureLineupResponseDto.PlayerAbsenceInfo> teamAbsences = absences.stream()
                .filter(absence -> team.getId().equals(absence.getTeam().getId()))
                .map(this::toAbsenceInfo)
                .toList();

        return FixtureLineupResponseDto.TeamLineup.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .formation(formation)
                .coachName(coachName)
                .colors(colorsOf(fixture, team))
                .starters(starters)
                .substitutes(substitutes)
                .absences(teamAbsences)
                .build();
    }

    private FixtureLineupResponseDto.UniformColors colorsOf(Fixture fixture, Team team) {
        if (fixture.getHomeTeam().getTeamId().equals(team.getTeamId())) {
            return FixtureLineupResponseDto.UniformColors.builder()
                    .player(colorOf(
                            fixture.getHomePlayerColorPrimary(),
                            fixture.getHomePlayerColorNumber(),
                            fixture.getHomePlayerColorBorder()
                    ))
                    .goalkeeper(colorOf(
                            fixture.getHomeGoalkeeperColorPrimary(),
                            fixture.getHomeGoalkeeperColorNumber(),
                            fixture.getHomeGoalkeeperColorBorder()
                    ))
                    .build();
        }

        return FixtureLineupResponseDto.UniformColors.builder()
                .player(colorOf(
                        fixture.getAwayPlayerColorPrimary(),
                        fixture.getAwayPlayerColorNumber(),
                        fixture.getAwayPlayerColorBorder()
                ))
                .goalkeeper(colorOf(
                        fixture.getAwayGoalkeeperColorPrimary(),
                        fixture.getAwayGoalkeeperColorNumber(),
                        fixture.getAwayGoalkeeperColorBorder()
                ))
                .build();
    }

    private FixtureLineupResponseDto.ColorInfo colorOf(String primary, String number, String border) {
        return FixtureLineupResponseDto.ColorInfo.builder()
                .primary(primary)
                .number(number)
                .border(border)
                .build();
    }

    private FixtureLineupResponseDto.PlayerLineup toPlayerLineup(FixtureLineup lineup) {
        return FixtureLineupResponseDto.PlayerLineup.builder()
                .playerId(lineup.getPlayer().getPlayerId())
                .playerName(lineup.getPlayer().getName())
                .backNumber(lineup.getJerseyNumber())
                .position(lineup.getPosition())
                .grid(lineup.getGrid())
                .starter(lineup.isStarter())
                .build();
    }

    private FixtureLineupResponseDto.PlayerAbsenceInfo toAbsenceInfo(PlayerAbsence absence) {
        return FixtureLineupResponseDto.PlayerAbsenceInfo.builder()
                .playerId(absence.getPlayer().getPlayerId())
                .playerName(absence.getPlayer().getName())
                .teamId(absence.getTeam().getTeamId())
                .teamName(absence.getTeam().getName())
                .absenceType(absence.getAbsenceType())
                .reason(absence.getReason())
                .build();
    }
}
