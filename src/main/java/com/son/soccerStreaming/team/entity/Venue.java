package com.son.soccerStreaming.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long venueId;

    private String venueName;
    private String venueAddress;
    private String venueCity;
    private Integer capacity;
    private String surface;
    private String venueImageUrl;

    public void updateVenue(String venueName, String venueAddress, String venueCity,
                            Integer capacity, String surface, String venueImageUrl) {
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.venueCity = venueCity;
        this.capacity = capacity;
        this.surface = surface;
        this.venueImageUrl = venueImageUrl;
    }
}
