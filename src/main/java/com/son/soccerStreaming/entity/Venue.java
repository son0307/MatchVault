package com.son.soccerStreaming.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    private Long venueApiId;
    private String venueName;
    private String venueAddress;
    private String venueCity;
    private Integer capacity;
    private String surface;
    private String venueImageUrl;
}
