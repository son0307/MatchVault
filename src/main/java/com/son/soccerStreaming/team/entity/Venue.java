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

import java.time.LocalDateTime;
import java.util.Objects;

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
    private String venueNameKo;
    private String venueAddress;
    private String venueCity;
    private Integer capacity;
    private String surface;
    private String venueImageUrl;
    private String venueImageObjectKey;
    private String adminVenueImageObjectKey;
    private LocalDateTime venueImageCachedAt;
    private LocalDateTime venueImageCacheFailedAt;
    private String venueImageCacheFailureReason;

    public void updateVenue(String venueName, String venueAddress, String venueCity,
                            Integer capacity, String surface, String venueImageUrl) {
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.venueCity = venueCity;
        this.capacity = capacity;
        this.surface = surface;
        updateVenueImageUrl(venueImageUrl);
    }

    public void updateKoreanName(String venueNameKo) {
        this.venueNameKo = venueNameKo == null || venueNameKo.isBlank()
                ? null
                : venueNameKo.trim();
    }

    public void updateVenueImageUrl(String venueImageUrl) {
        if (!Objects.equals(this.venueImageUrl, venueImageUrl)) {
            this.venueImageObjectKey = null;
            this.venueImageCachedAt = null;
            this.venueImageCacheFailedAt = null;
            this.venueImageCacheFailureReason = null;
        }
        this.venueImageUrl = venueImageUrl;
    }

    public void markVenueImageCached(String objectKey, LocalDateTime cachedAt) {
        this.venueImageObjectKey = objectKey;
        this.venueImageCachedAt = cachedAt;
        this.venueImageCacheFailedAt = null;
        this.venueImageCacheFailureReason = null;
    }

    public void updateAdminVenueImageObjectKey(String objectKey) {
        this.adminVenueImageObjectKey = objectKey;
    }

    public void clearAdminVenueImageObjectKey() {
        this.adminVenueImageObjectKey = null;
    }

    public void markVenueImageCacheFailed(LocalDateTime failedAt, String failureReason) {
        this.venueImageCacheFailedAt = failedAt;
        this.venueImageCacheFailureReason = failureReason;
    }
}
