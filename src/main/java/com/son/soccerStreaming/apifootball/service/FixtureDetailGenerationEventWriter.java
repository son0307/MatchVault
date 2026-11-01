package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureDetailGenerationEventWriter {

    private final FixtureDetailGenerationStore generationStore;
    private final TeamRepository teamRepository;
    private final ApiFootballPlayerSyncService playerSyncService;
    private final AdminOverrideService adminOverrideService;

    public void apply(long generationId, Fixture fixture, List<ApiFootballLiveDto.EventResponse> events) {
        if (events == null || events.isEmpty() || adminOverrideService.isOverriddenForEventSync(
                AdminOverrideTargetType.FIXTURE_EVENT, fixture.getFixtureId(), "events")) {
            return;
        }
        List<FixtureDetailGenerationStore.EventRow> rows = new ArrayList<>(events.size());
        int sequence = 1;
        for (ApiFootballLiveDto.EventResponse event : events) {
            if (event == null) {
                throw new IllegalArgumentException("Fixture detail event response contains null");
            }
            Team team = event.getTeam() != null && event.getTeam().getId() != null
                    ? teamRepository.findByTeamId(event.getTeam().getId()).orElseThrow(() ->
                    new IllegalArgumentException("Fixture event refers to an unknown team"))
                    : null;
            Player player = resolvePlayer(event.getPlayer(), team);
            Player assist = resolvePlayer(event.getAssist(), team);
            rows.add(new FixtureDetailGenerationStore.EventRow(
                    sequence++,
                    event.getTime() != null ? event.getTime().getElapsed() : null,
                    event.getTime() != null ? event.getTime().getExtra() : null,
                    team != null ? team.getId() : null,
                    player != null ? player.getId() : null,
                    assist != null ? assist.getId() : null,
                    event.getType(), event.getDetail(), event.getComments()
            ));
        }
        generationStore.applyEvents(generationId, fixture.getId(), rows);
    }

    private Player resolvePlayer(ApiFootballLiveDto.PlayerInfo info, Team team) {
        if (info == null || info.getId() == null || info.getId() <= 0) {
            return null;
        }
        return playerSyncService.findOrFetchPlayer(
                info.getId(), info.getName(), team, null, null, info.getPhoto()).orElseThrow(() ->
                new IllegalArgumentException("Fixture event refers to an unknown player: " + info.getId()));
    }
}
