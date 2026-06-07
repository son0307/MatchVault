package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUrlService {

    private final MediaProperties properties;

    public String playerPhotoUrl(Player player) {
        if (player == null) {
            return null;
        }
        return resolve(player.getPhotoObjectKey(), player.getPhotoUrl());
    }

    public String teamLogoUrl(Team team) {
        if (team == null) {
            return null;
        }
        return resolve(team.getLogoObjectKey(), team.getLogoUrl());
    }

    public String venueImageUrl(Venue venue) {
        if (venue == null) {
            return null;
        }
        return resolve(venue.getVenueImageObjectKey(), venue.getVenueImageUrl());
    }

    public String resolve(String objectKey, String sourceUrl) {
        String baseUrl = properties.getPublicBaseUrl();
        if (objectKey == null || objectKey.isBlank()) {
            return sourceUrl;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Media object key exists but media.public-base-url is not configured. Falling back to source URL.");
            return sourceUrl;
        }
        return baseUrl.replaceAll("/+$", "") + "/" + objectKey;
    }
}
