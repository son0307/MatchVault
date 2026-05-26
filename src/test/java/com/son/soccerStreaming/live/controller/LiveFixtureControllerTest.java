package com.son.soccerStreaming.live.controller;

import com.son.soccerStreaming.fixture.service.FixturePlayerStatService;
import com.son.soccerStreaming.fixture.service.FixtureRedisService;
import com.son.soccerStreaming.fixture.service.FixtureStatService;
import com.son.soccerStreaming.live.service.LiveFixtureQueryService;
import com.son.soccerStreaming.live.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveFixtureControllerTest {

    private SseService sseService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sseService = mock(SseService.class);
        LiveFixtureController controller = new LiveFixtureController(
                sseService,
                mock(FixtureRedisService.class),
                mock(FixtureStatService.class),
                mock(FixturePlayerStatService.class),
                mock(LiveFixtureQueryService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void streamRejectsNonNumericFixtureId() throws Exception {
        mockMvc.perform(get("/api/v1/live/stream/fixtures/not-number"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sseService);
    }
}
