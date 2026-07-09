package com.son.soccerStreaming.admin.repository;

import com.son.soccerStreaming.admin.entity.AdminSyncJob;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;

public interface AdminSyncJobRepository extends JpaRepository<AdminSyncJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AdminSyncJob j where j.id = :jobId")
    java.util.Optional<AdminSyncJob> findByIdForUpdate(@Param("jobId") Long jobId);

    @EntityGraph(attributePaths = "adminUser")
    List<AdminSyncJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AdminSyncJob> findAllByStatusIn(Collection<AdminSyncJobStatus> statuses);

    @EntityGraph(attributePaths = "adminUser")
    List<AdminSyncJob> findAllByStatusInOrderByCreatedAtDesc(Collection<AdminSyncJobStatus> statuses);

    @EntityGraph(attributePaths = "adminUser")
    List<AdminSyncJob> findAllByStatusNotInOrderByCompletedAtDesc(
            Collection<AdminSyncJobStatus> statuses, Pageable pageable);
}
