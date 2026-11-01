package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationReadStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.apifootball.service.FixtureDetailLiveChangeStore;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import com.son.soccerStreaming.fixture.repository.FixtureEventRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureEventServiceTest {

    @Mock private FixtureRepository fixtureRepository;
    @Mock private FixtureEventRepository fixtureEventRepository;
    @Mock private MediaUrlService mediaUrlService;
    @Mock private FixtureDetailGenerationResolver generationResolver;
    @Mock private FixtureDetailGenerationReadStore generationReadStore;
    @Mock private FixtureDetailLiveChangeStore liveChangeStore;
    @InjectMocks private FixtureEventService service;

    @Test
    void readsEventsFromSamePublishedGenerationAsFixture() {
        Fixture fixture = Fixture.builder().id(11L).fixtureId(100L).season(2025).build();
        FixtureEvent event = FixtureEvent.builder()
                .fixture(fixture).eventSequence(1).eventType("Goal").build();
        when(fixtureRepository.findByFixtureId(100L)).thenReturn(Optional.of(fixture));
        when(generationResolver.activeForSeason(2025)).thenReturn(7L);
        when(generationReadStore.findFixture(7L, 100L)).thenReturn(Optional.of(fixture));
        when(generationReadStore.findEvents(7L, fixture)).thenReturn(List.of(event));

        assertThat(service.getFixtureEvents(100L).getEvents())
                .extracting(item -> item.getType()).containsExactly("Goal");
        verify(fixtureEventRepository, never()).findAllByFixtureFixtureIdOrderByMatchTimeAsc(100L);
    }
}
