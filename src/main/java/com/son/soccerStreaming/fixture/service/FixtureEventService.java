package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.fixture.dto.FixtureEventResponseDto;
import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureEventRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureEventService {

    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository fixtureEventRepository;

    @Transactional(readOnly = true)
    public FixtureEventResponseDto getFixtureEvents(Long fixtureId) {
        if (fixtureRepository.findByFixtureId(fixtureId).isEmpty()) {
            throw new CustomException(ErrorCode.FIXTURE_NOT_FOUND);
        }

        List<FixtureEventResponseDto.Event> events = fixtureEventRepository
                .findAllByFixtureFixtureIdOrderByMatchTimeAsc(fixtureId)
                .stream()
                .map(this::toEvent)
                .toList();

        return FixtureEventResponseDto.builder()
                .fixtureId(fixtureId)
                .events(events)
                .build();
    }

    private FixtureEventResponseDto.Event toEvent(FixtureEvent event) {
        return FixtureEventResponseDto.Event.builder()
                .sequence(event.getEventSequence())
                .time(FixtureEventResponseDto.TimeInfo.builder()
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

    private FixtureEventResponseDto.TeamInfo toTeamInfo(Team team) {
        if (team == null) {
            return null;
        }

        return FixtureEventResponseDto.TeamInfo.builder()
                .id(team.getTeamId())
                .name(team.getName())
                .logo(team.getLogoUrl())
                .build();
    }

    private FixtureEventResponseDto.PlayerInfo toPlayerInfo(Player player) {
        if (player == null) {
            return null;
        }

        return FixtureEventResponseDto.PlayerInfo.builder()
                .id(player.getPlayerId())
                .name(player.getName())
                .build();
    }
}
