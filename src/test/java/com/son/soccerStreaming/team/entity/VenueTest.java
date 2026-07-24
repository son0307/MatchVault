package com.son.soccerStreaming.team.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VenueTest {

    @Test
    void normalizesKoreanName() {
        Venue venue = Venue.builder()
                .venueId(593L)
                .build();

        venue.updateKoreanName("  토트넘 홋스퍼 스타디움  ");
        assertThat(venue.getVenueNameKo()).isEqualTo("토트넘 홋스퍼 스타디움");

        venue.updateKoreanName("   ");
        assertThat(venue.getVenueNameKo()).isNull();
    }
}
