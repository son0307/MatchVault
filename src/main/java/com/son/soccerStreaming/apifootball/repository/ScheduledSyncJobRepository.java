package com.son.soccerStreaming.apifootball.repository;

import com.son.soccerStreaming.apifootball.entity.ScheduledSyncJob;
import com.son.soccerStreaming.apifootball.entity.ScheduledSyncJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduledSyncJobRepository extends JpaRepository<ScheduledSyncJob, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ScheduledSyncJob j where j.jobId = :jobId")
    Optional<ScheduledSyncJob> findByIdForUpdate(@Param("jobId") String jobId);

    boolean existsByActiveSyncKey(String activeSyncKey);

    List<ScheduledSyncJob> findAllByStatusIn(Collection<ScheduledSyncJobStatus> statuses);
}
