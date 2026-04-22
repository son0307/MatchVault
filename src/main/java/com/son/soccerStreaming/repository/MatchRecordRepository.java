package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.MatchRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {
    Optional<MatchRecord> findByMatchId(String matchId);

    // 첫 페이지 조회
    List<MatchRecord> findAllByOrderByIdDesc(Pageable pageable);

    // 두 번째 페이지부터 조회
    List<MatchRecord> findByIdLessThanOrderByIdDesc(Long id, Pageable pageable);
}
