package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCacheMetadataTest {

    @Test
    void playerPhotoCacheIsClearedWhenSourceUrlChanges() {
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .photoUrl("old.png")
                .build();
        player.markPhotoCached("api-football/players/7.png", LocalDateTime.now());

        player.updatePhotoUrl("new.png");

        assertThat(player.getPhotoObjectKey()).isNull();
        assertThat(player.getPhotoCachedAt()).isNull();
        assertThat(player.getPhotoCacheFailedAt()).isNull();
        assertThat(player.getPhotoCacheFailureReason()).isNull();
    }

    @Test
    void teamLogoCacheIsClearedWhenSourceUrlChanges() {
        Team team = Team.builder()
                .teamId(47L)
                .name("Tottenham")
                .logoUrl("old.png")
                .build();
        team.markLogoCached("api-football/teams/47.png", LocalDateTime.now());

        team.updateLogoUrl("new.png");

        assertThat(team.getLogoObjectKey()).isNull();
        assertThat(team.getLogoCachedAt()).isNull();
        assertThat(team.getLogoCacheFailedAt()).isNull();
        assertThat(team.getLogoCacheFailureReason()).isNull();
    }

    @Test
    void venueImageCacheIsClearedWhenSourceUrlChanges() {
        Venue venue = Venue.builder()
                .venueId(593L)
                .venueName("Tottenham Hotspur Stadium")
                .venueImageUrl("old.png")
                .build();
        venue.markVenueImageCached("api-football/venues/593.png", LocalDateTime.now());

        venue.updateVenueImageUrl("new.png");

        assertThat(venue.getVenueImageObjectKey()).isNull();
        assertThat(venue.getVenueImageCachedAt()).isNull();
        assertThat(venue.getVenueImageCacheFailedAt()).isNull();
        assertThat(venue.getVenueImageCacheFailureReason()).isNull();
    }
}
