package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.dto.FixtureEventDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureEventRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureEventSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final FixtureEventRepository fixtureEventRepository;
    private final TeamRepository teamRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;

    @Transactional
    public FixtureEventDto syncEvents(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<ApiFootballLiveDto.EventResponse> events = apiFootballClient.getEvents(fixtureId);
        return syncEvents(fixture, events);
    }

    @Transactional
    public FixtureEventDto syncEvents(Long fixtureId, List<ApiFootballLiveDto.EventResponse> events) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        return syncEvents(fixture, events);
    }

    @Transactional
    public FixtureEventDto syncEvents(Fixture fixture, List<ApiFootballLiveDto.EventResponse> events) {
        FixtureEventDto latestEvent = null;
        int sequence = 1;

        if (events == null || events.isEmpty()) {
            return null;
        }

        for (ApiFootballLiveDto.EventResponse event : events) {
            latestEvent = toFixtureEventDto(fixture.getFixtureId(), event);

            Team team = findTeam(event.getTeam()).orElse(null);
            Player player = findPlayer(event.getPlayer(), team).orElse(null);
            Player assistPlayer = findPlayer(event.getAssist(), team).orElse(null);
            int currentSequence = sequence;

            FixtureEvent entity = fixtureEventRepository
                    .findByFixtureFixtureIdAndEventSequence(fixture.getFixtureId(), currentSequence)
                    .orElseGet(() -> FixtureEvent.builder()
                            .fixture(fixture)
                            .eventSequence(currentSequence)
                            .build());

            entity.updateEvent(
                    event.getTime() != null ? event.getTime().getElapsed() : null,
                    event.getTime() != null ? event.getTime().getExtra() : null,
                    team,
                    player,
                    assistPlayer,
                    event.getType(),
                    event.getDetail(),
                    event.getComments()
            );
            fixtureEventRepository.save(entity);
            sequence++;
        }

        return latestEvent;
    }

    @Transactional
    public int syncEvents(List<Fixture> fixtures) {
        int syncedCount = 0;
        for (Fixture fixture : fixtures) {
            try {
                syncEvents(fixture.getFixtureId());
                syncedCount++;
            } catch (Exception e) {
                log.error("API-Football fixture event sync failed. fixtureId={}", fixture.getFixtureId(), e);
            }
        }
        return syncedCount;
    }

    private FixtureEventDto toFixtureEventDto(Long fixtureId, ApiFootballLiveDto.EventResponse event) {
        return FixtureEventDto.builder()
                .fixtureId(fixtureId)
                .time(event.getTime() == null ? null : FixtureEventDto.TimeInfo.builder()
                        .elapsed(event.getTime().getElapsed())
                        .extra(event.getTime().getExtra())
                        .build())
                .team(toTeamInfo(event.getTeam()))
                .player(toPlayerInfo(event.getPlayer()))
                .assist(toPlayerInfo(event.getAssist()))
                .type(event.getType())
                .detail(event.getDetail())
                .comments(event.getComments())
                .build();
    }

    private FixtureEventDto.TeamInfo toTeamInfo(ApiFootballLiveDto.TeamInfo team) {
        if (team == null) {
            return null;
        }
        return FixtureEventDto.TeamInfo.builder()
                .id(team.getId())
                .name(team.getName())
                .logo(team.getLogo())
                .build();
    }

    private FixtureEventDto.PlayerInfo toPlayerInfo(ApiFootballLiveDto.PlayerInfo player) {
        if (player == null) {
            return null;
        }
        return FixtureEventDto.PlayerInfo.builder()
                .id(player.getId())
                .name(player.getName())
                .build();
    }

    private Optional<Team> findTeam(ApiFootballLiveDto.TeamInfo team) {
        if (team == null || team.getId() == null) {
            return Optional.empty();
        }
        return teamRepository.findByTeamId(team.getId());
    }

    private Optional<Player> findPlayer(ApiFootballLiveDto.PlayerInfo player, Team team) {
        if (player == null || player.getId() == null) {
            return Optional.empty();
        }

        return apiFootballPlayerSyncService.findOrFetchPlayer(
                player.getId(),
                player.getName(),
                team,
                null,
                null,
                player.getPhoto()
        );
    }
}
