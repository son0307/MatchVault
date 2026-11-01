package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureDetailGenerationResolverTest {

    @Mock private FixtureDetailGenerationStore generationStore;
    @InjectMocks private FixtureDetailGenerationResolver resolver;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void pinsGenerationAcrossMultipleReadsInOneRequest() {
        ReflectionTestUtils.setField(resolver, "enabled", true);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        when(generationStore.activeGenerationId(2025)).thenReturn(7L);

        assertThat(resolver.activeForSeason(2025)).isEqualTo(7L);
        assertThat(resolver.activeForSeason(2025)).isEqualTo(7L);

        verify(generationStore, times(1)).activeGenerationId(2025);
    }

    @Test
    void disabledReadsDoNotRequireMigrationTables() {
        assertThat(resolver.activeForSeason(2025)).isNull();
        verifyNoInteractions(generationStore);
    }
}
