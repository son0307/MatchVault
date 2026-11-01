package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApiFootballPlayerSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private PlayerRepository playerRepository;
    @InjectMocks private ApiFootballPlayerSyncService service;

    @Test
    void doesNotFetchPlaceholderPlayerProfile() {
        assertThat(service.findOrFetchPlayer(0L, "Unknown", null, null, null, null)).isEmpty();
        assertThat(service.fetchProfile(0L, null, null)).isEmpty();
        verifyNoInteractions(apiFootballClient, playerRepository);
    }
}
