package com.son.soccerStreaming.team.repository;

import com.son.soccerStreaming.team.entity.Venue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    Optional<Venue> findByVenueId(Long venueId);

    @Query("""
            select v.id
            from Venue v
            where v.venueImageUrl is not null
              and v.venueImageUrl <> ''
              and v.venueImageObjectKey is null
              and (v.venueImageCacheFailedAt is null or v.venueImageCacheFailedAt < :retryBefore)
            order by v.venueName asc
            """)
    List<Long> findImageCacheCandidateIds(LocalDateTime retryBefore, Pageable pageable);

    @Query("select v.adminVenueImageObjectKey from Venue v where v.adminVenueImageObjectKey is not null")
    List<String> findAllAdminVenueImageObjectKeys();
}
