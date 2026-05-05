package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchLineupResponseDto;
import com.son.soccerStreaming.entity.MatchLineup;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.entity.PlayerAbsence;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchLineupRepository;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import com.son.soccerStreaming.repository.PlayerAbsenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchLineupService {

    private final MatchLineupRepository matchLineupRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayerAbsenceRepository playerAbsenceRepository;

    public MatchLineupResponseDto.Lineup getMatchLineups(Long matchId) {
        MatchRecord match = matchRecordRepository.findByApiFixtureId(matchId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));
        List<MatchLineup> lineups = matchLineupRepository.findAllByMatchId(matchId);
        List<PlayerAbsence> absences = playerAbsenceRepository.findAllByMatchRecordApiFixtureId(matchId);

        return MatchLineupResponseDto.Lineup.builder()
                .matchId(matchId)
                .homeTeam(buildTeamLineup(
                        match.getHomeTeam(),
                        match.getHomeFormation(),
                        match.getHomeCoachName(),
                        lineups,
                        absences
                ))
                .awayTeam(buildTeamLineup(
                        match.getAwayTeam(),
                        match.getAwayFormation(),
                        match.getAwayCoachName(),
                        lineups,
                        absences
                ))
                .build();
    }

    private MatchLineupResponseDto.TeamLineup buildTeamLineup(
            Team team,
            String formation,
            String coachName,
            List<MatchLineup> lineups,
            List<PlayerAbsence> absences
    ) {
        List<MatchLineupResponseDto.PlayerLineup> starters = lineups.stream()
                .filter(lineup -> team.getId().equals(lineup.getTeam().getId()))
                .filter(MatchLineup::isStarter)
                .map(this::toPlayerLineup)
                .toList();

        List<MatchLineupResponseDto.PlayerLineup> substitutes = lineups.stream()
                .filter(lineup -> team.getId().equals(lineup.getTeam().getId()))
                .filter(lineup -> !lineup.isStarter())
                .map(this::toPlayerLineup)
                .toList();

        List<MatchLineupResponseDto.PlayerAbsenceInfo> teamAbsences = absences.stream()
                .filter(absence -> team.getId().equals(absence.getTeam().getId()))
                .map(this::toAbsenceInfo)
                .toList();

        return MatchLineupResponseDto.TeamLineup.builder()
                .teamId(team.getTeamApiId())
                .teamName(team.getName())
                .formation(formation)
                .coachName(coachName)
                .starters(starters)
                .substitutes(substitutes)
                .absences(teamAbsences)
                .build();
    }

    private MatchLineupResponseDto.PlayerLineup toPlayerLineup(MatchLineup lineup) {
        return MatchLineupResponseDto.PlayerLineup.builder()
                .playerId(lineup.getPlayer().getApiPlayerId())
                .playerName(lineup.getPlayer().getName())
                .backNumber(lineup.getJerseyNumber())
                .position(lineup.getPosition())
                .grid(lineup.getGrid())
                .starter(lineup.isStarter())
                .build();
    }

    private MatchLineupResponseDto.PlayerAbsenceInfo toAbsenceInfo(PlayerAbsence absence) {
        return MatchLineupResponseDto.PlayerAbsenceInfo.builder()
                .playerId(absence.getPlayer().getApiPlayerId())
                .playerName(absence.getPlayer().getName())
                .teamId(absence.getTeam().getTeamApiId())
                .teamName(absence.getTeam().getName())
                .absenceType(absence.getAbsenceType())
                .reason(absence.getReason())
                .build();
    }
}
