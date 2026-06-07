package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUrlServiceTest {

    private final MediaProperties properties = new MediaProperties();
    private final MediaUrlService mediaUrlService = new MediaUrlService(properties);

    @Test
    void resolvesCdnUrlWhenObjectKeyExists() {
        properties.setPublicBaseUrl("https://media.example.com/");
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .photoUrl("https://media.api-sports.io/football/players/7.png")
                .build();
        player.markPhotoCached("api-football/players/7.png", LocalDateTime.now());

        assertThat(mediaUrlService.playerPhotoUrl(player))
                .isEqualTo("https://media.example.com/api-football/players/7.png");
    }

    @Test
    void fallsBackToSourceUrlWhenObjectKeyIsMissing() {
        Team team = Team.builder()
                .teamId(47L)
                .name("Tottenham")
                .logoUrl("https://media.api-sports.io/football/teams/47.png")
                .build();

        assertThat(mediaUrlService.teamLogoUrl(team))
                .isEqualTo("https://media.api-sports.io/football/teams/47.png");
    }

    @Test
    void resolvesVenueImageUrl() {
        properties.setPublicBaseUrl("https://media.example.com");
        Venue venue = Venue.builder()
                .venueId(593L)
                .venueName("Tottenham Hotspur Stadium")
                .venueImageUrl("https://media.api-sports.io/football/venues/593.png")
                .build();
        venue.markVenueImageCached("api-football/venues/593.png", LocalDateTime.now());

        assertThat(mediaUrlService.venueImageUrl(venue))
                .isEqualTo("https://media.example.com/api-football/venues/593.png");
    }
}
