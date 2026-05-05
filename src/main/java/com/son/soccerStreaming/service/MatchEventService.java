package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchEventResponseDto;
import com.son.soccerStreaming.entity.MatchEvent;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchEventRepository;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchEventService {

    private final MatchRecordRepository matchRecordRepository;
    private final MatchEventRepository matchEventRepository;

    @Transactional(readOnly = true)
    public MatchEventResponseDto getMatchEvents(Long fixtureId) {
        if (matchRecordRepository.findByApiFixtureId(fixtureId).isEmpty()) {
            throw new CustomException(ErrorCode.MATCH_NOT_FOUND);
        }

        List<MatchEventResponseDto.Event> events = matchEventRepository
                .findAllByMatchRecordApiFixtureIdOrderByElapsedAscEventSequenceAsc(fixtureId)
                .stream()
                .map(this::toEvent)
                .toList();

        return MatchEventResponseDto.builder()
                .matchId(fixtureId)
                .events(events)
                .build();
    }

    private MatchEventResponseDto.Event toEvent(MatchEvent event) {
        return MatchEventResponseDto.Event.builder()
                .sequence(event.getEventSequence())
                .time(MatchEventResponseDto.TimeInfo.builder()
                        .elapsed(event.getElapsed())
                        .extra(event.getExtra())
                        .build())
                .team(toTeamInfo(event.getTeam()))
                .player(toPlayerInfo(event.getPlayer()))
                .assist(toPlayerInfo(event.getAssistPlayer()))
                .type(event.getEventType())
                .detail(event.getEventDetail())
                .comments(event.getComments())
                .build();
    }

    private MatchEventResponseDto.TeamInfo toTeamInfo(Team team) {
        if (team == null) {
            return null;
        }

        return MatchEventResponseDto.TeamInfo.builder()
                .id(team.getTeamApiId())
                .name(team.getName())
                .logo(team.getLogoUrl())
                .build();
    }

    private MatchEventResponseDto.PlayerInfo toPlayerInfo(Player player) {
        if (player == null) {
            return null;
        }

        return MatchEventResponseDto.PlayerInfo.builder()
                .id(player.getApiPlayerId())
                .name(player.getName())
                .build();
    }
}
