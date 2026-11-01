package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FixtureDetailGenerationEventWriterTest {

    @Mock private FixtureDetailGenerationStore generationStore;
    @Mock private TeamRepository teamRepository;
    @Mock private ApiFootballPlayerSyncService playerSyncService;
    @Mock private AdminOverrideService adminOverrideService;
    @InjectMocks private FixtureDetailGenerationEventWriter writer;

    @Test
    void preservesApiEventOrderInPrivateGeneration() {
        Fixture fixture = Fixture.builder().id(11L).fixtureId(100L).build();
        ApiFootballLiveDto.EventResponse first = new ApiFootballLiveDto.EventResponse();
        ReflectionTestUtils.setField(first, "type", "Goal");
        ApiFootballLiveDto.EventResponse second = new ApiFootballLiveDto.EventResponse();
        ReflectionTestUtils.setField(second, "type", "Card");

        writer.apply(7L, fixture, List.of(first, second));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FixtureDetailGenerationStore.EventRow>> rows =
                ArgumentCaptor.forClass(List.class);
        verify(generationStore).applyEvents(eq(7L), eq(11L), rows.capture());
        assertThat(rows.getValue()).extracting(FixtureDetailGenerationStore.EventRow::sequence)
                .containsExactly(1, 2);
        assertThat(rows.getValue()).extracting(FixtureDetailGenerationStore.EventRow::type)
                .containsExactly("Goal", "Card");
    }

    @Test
    void keepsEventWithoutPlaceholderPlayerId() {
        Fixture fixture = Fixture.builder().id(11L).fixtureId(100L).build();
        ApiFootballLiveDto.PlayerInfo player = new ApiFootballLiveDto.PlayerInfo();
        ReflectionTestUtils.setField(player, "id", 0L);
        ApiFootballLiveDto.EventResponse event = new ApiFootballLiveDto.EventResponse();
        ReflectionTestUtils.setField(event, "player", player);

        writer.apply(7L, fixture, List.of(event));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FixtureDetailGenerationStore.EventRow>> rows =
                ArgumentCaptor.forClass(List.class);
        verify(generationStore).applyEvents(eq(7L), eq(11L), rows.capture());
        assertThat(rows.getValue()).singleElement()
                .extracting(FixtureDetailGenerationStore.EventRow::playerId).isNull();
        verifyNoInteractions(playerSyncService);
    }
}
