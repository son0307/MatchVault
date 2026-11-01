package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationStore;
import com.son.soccerStreaming.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminFixtureGenerationGuardTest {

    private final FixtureDetailGenerationResolver resolver = mock(FixtureDetailGenerationResolver.class);
    private final FixtureDetailGenerationStore store = mock(FixtureDetailGenerationStore.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminFixtureGenerationGuard guard = new AdminFixtureGenerationGuard(resolver, store, jdbc);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void rejectsAnEditStartedBeforeAnotherGenerationWasPublished() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/admin/fixtures/100");
        request.addHeader(AdminFixtureGenerationGuard.HEADER, "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(resolver.enabled()).thenReturn(true);
        when(jdbc.queryForList("SELECT season FROM fixture_detail_season_lock WHERE season = ? FOR UPDATE",
                Integer.class, 2025)).thenReturn(List.of(2025));
        when(store.activeGenerationId(2025)).thenReturn(2L);

        assertThatThrownBy(() -> guard.verifyMutation(2025))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void acceptsTheCurrentGenerationWhileHoldingTheSeasonLock() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/admin/fixtures/100");
        request.addHeader(AdminFixtureGenerationGuard.HEADER, "2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(resolver.enabled()).thenReturn(true);
        when(jdbc.queryForList("SELECT season FROM fixture_detail_season_lock WHERE season = ? FOR UPDATE",
                Integer.class, 2025)).thenReturn(List.of(2025));
        when(store.activeGenerationId(2025)).thenReturn(2L);

        guard.verifyMutation(2025);

        verify(store).activeGenerationId(2025);
    }
}
