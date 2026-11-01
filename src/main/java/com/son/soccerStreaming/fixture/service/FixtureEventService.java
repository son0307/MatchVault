package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationReadStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.apifootball.service.FixtureDetailLiveChangeStore;
import com.son.soccerStreaming.fixture.dto.FixtureEventResponseDto;
import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureEventRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureEventService {

    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository fixtureEventRepository;
    private final MediaUrlService mediaUrlService;
    private final FixtureDetailGenerationResolver generationResolver;
    private final FixtureDetailGenerationReadStore generationReadStore;
    private final FixtureDetailLiveChangeStore liveChangeStore;

    @Transactional(readOnly = true)
    public FixtureEventResponseDto getFixtureEvents(Long fixtureId) {
        var publishedFixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        Long generationId = publishedFixture.getSeason() == null ? null
                : generationResolver.activeForSeason(publishedFixture.getSeason());

        List<FixtureEvent> source = generationId == null || liveChangeStore.changed(
                publishedFixture.getSeason(), fixtureId, FixtureDetailLiveChangeStore.Part.EVENT)
                ? fixtureEventRepository.findAllByFixtureFixtureIdOrderByMatchTimeAsc(fixtureId)
                : generationReadStore.findEvents(generationId,
                        generationReadStore.findFixture(generationId, fixtureId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Published generation is missing fixture " + fixtureId)));
        List<FixtureEventResponseDto.Event> events = source
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
                .logo(mediaUrlService.teamLogoUrl(team))
                .build();
    }

    private FixtureEventResponseDto.PlayerInfo toPlayerInfo(Player player) {
        if (player == null) {
            return null;
        }

        return FixtureEventResponseDto.PlayerInfo.builder()
                .id(player.getPlayerId())
                .name(player.getName())
                .nameKo(player.getKoreanName())
                .build();
    }
}
