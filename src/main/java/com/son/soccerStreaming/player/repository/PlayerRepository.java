package com.son.soccerStreaming.player.repository;

import com.son.soccerStreaming.player.entity.Player;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByPlayerId(Long playerId);

    boolean existsByPlayerId(Long playerId);

    @Query("""
            select p
            from Player p
            where lower(p.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.koreanName, '')) like lower(concat('%', :keyword, '%'))
            order by p.name asc
            """)
    List<Player> findTop20ByNameOrKoreanNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    @Query("""
            select p.id
            from Player p
            where p.photoUrl is not null
              and p.photoUrl <> ''
              and p.photoObjectKey is null
              and (p.photoCacheFailedAt is null or p.photoCacheFailedAt < :retryBefore)
            order by p.name asc
            """)
    List<Long> findPhotoCacheCandidateIds(LocalDateTime retryBefore, Pageable pageable);

    @Query("select p.adminPhotoObjectKey from Player p where p.adminPhotoObjectKey is not null")
    List<String> findAllAdminPhotoObjectKeys();
}
