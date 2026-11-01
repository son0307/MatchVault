package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballFixtureSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    @Mock private ApiFootballSyncStatusService apiFootballSyncStatusService;
    @Mock private AdminOverrideService adminOverrideService;
    @Mock private OptimisticLockRetryExecutor optimisticLockRetryExecutor;

    @InjectMocks
    private ApiFootballFixtureSyncService service;

    @Test
    void fixtureSyncKeepsAdminOverriddenMetadataFields() {
        LocalDateTime adminDate = LocalDateTime.of(2026, 8, 7, 10, 0);
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .fixtureDate(adminDate)
                .referee("Admin referee")
                .venueId(1L)
                .venueName("Admin stadium")
                .venueCity("Old city")
                .build();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        ReflectionTestUtils.setField(fixtureInfo, "date", "2026-08-08T12:00:00Z");
        ReflectionTestUtils.setField(fixtureInfo, "referee", "API referee");
        ApiFootballLiveDto.Venue venue = new ApiFootballLiveDto.Venue();
        ReflectionTestUtils.setField(venue, "id", 2L);
        ReflectionTestUtils.setField(venue, "name", "API stadium");
        ReflectionTestUtils.setField(venue, "city", "New city");
        ReflectionTestUtils.setField(fixtureInfo, "venue", venue);
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        keepCurrentValueWhenOverridden();

        ReflectionTestUtils.invokeMethod(
                service,
                "updateFixture",
                fixture,
                response,
                Set.of("fixtureDate", "venueName")
        );

        assertThat(fixture.getFixtureDate()).isEqualTo(adminDate);
        assertThat(fixture.getVenueName()).isEqualTo("Admin stadium");
        assertThat(fixture.getReferee()).isEqualTo("API referee");
        assertThat(fixture.getVenueId()).isEqualTo(2L);
        assertThat(fixture.getVenueCity()).isEqualTo("New city");
    }

    @Test
    void fixtureSyncStoresElapsedAndAddedTime() {
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .fixtureDate(LocalDateTime.of(2026, 8, 8, 12, 0))
                .build();
        ApiFootballLiveDto.Status status = new ApiFootballLiveDto.Status();
        ReflectionTestUtils.setField(status, "shortStatus", "2H");
        ReflectionTestUtils.setField(status, "longStatus", "Second Half");
        ReflectionTestUtils.setField(status, "elapsed", 90);
        ReflectionTestUtils.setField(status, "extra", 6);
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        ReflectionTestUtils.setField(fixtureInfo, "status", status);
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        keepCurrentValueWhenOverridden();

        ReflectionTestUtils.invokeMethod(service, "updateFixture", fixture, response, Set.of());

        assertThat(fixture.getFixtureStatus()).isEqualTo("LIVE");
        assertThat(fixture.getElapsed()).isEqualTo(90);
        assertThat(fixture.getExtra()).isEqualTo(6);
    }

    @Test
    void previewAppliesResponseWithoutChangingPublishedFixture() {
        Fixture current = Fixture.builder()
                .fixtureId(1000L)
                .fixtureDate(LocalDateTime.of(2026, 8, 8, 12, 0))
                .homeScore(0)
                .awayScore(0)
                .build();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        ReflectionTestUtils.setField(fixtureInfo, "id", 1000L);
        ApiFootballLiveDto.Goals goals = new ApiFootballLiveDto.Goals();
        ReflectionTestUtils.setField(goals, "home", 1);
        ReflectionTestUtils.setField(goals, "away", 0);
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        ReflectionTestUtils.setField(response, "goals", goals);
        ApiFootballLiveDto.TeamInfo home = new ApiFootballLiveDto.TeamInfo();
        ApiFootballLiveDto.TeamInfo away = new ApiFootballLiveDto.TeamInfo();
        ReflectionTestUtils.setField(home, "id", 1L);
        ReflectionTestUtils.setField(away, "id", 2L);
        ApiFootballLiveDto.Teams teams = new ApiFootballLiveDto.Teams();
        ReflectionTestUtils.setField(teams, "home", home);
        ReflectionTestUtils.setField(teams, "away", away);
        ReflectionTestUtils.setField(response, "teams", teams);
        when(adminOverrideService.overriddenFields(eq(AdminOverrideTargetType.FIXTURE), eq(1000L), any()))
                .thenReturn(Set.of());
        keepCurrentValueWhenOverridden();

        Fixture draft = service.previewFixtureResponse(current, response);

        assertThat(draft).isNotSameAs(current);
        assertThat(draft.getHomeScore()).isEqualTo(1);
        assertThat(current.getHomeScore()).isZero();
        verify(fixtureRepository, never()).save(any());
    }

    private void keepCurrentValueWhenOverridden() {
        doAnswer(invocation -> {
            Set<?> overrides = invocation.getArgument(0);
            String fieldName = invocation.getArgument(1);
            return overrides.contains(fieldName) ? invocation.getArgument(2) : invocation.getArgument(3);
        }).when(adminOverrideService).apiValueUnlessOverridden(any(), anyString(), any(), any());
    }
}
