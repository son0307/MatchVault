package com.son.soccerStreaming.team.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long teamId;

    @Column(nullable = false)
    private String name;
    private String code;
    private String country;
    private Integer founded;
    private String logoUrl;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    public void updateTeam(String name, String code, String country, Integer founded, String logoUrl) {
        this.name = name;
        this.code = code;
        this.country = country;
        this.founded = founded;
        this.logoUrl = logoUrl;
    }

    public void updateVenue(Venue venue) {
        this.venue = venue;
    }
}
