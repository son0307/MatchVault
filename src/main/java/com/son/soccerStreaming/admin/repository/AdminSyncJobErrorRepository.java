package com.son.soccerStreaming.admin.repository;

import com.son.soccerStreaming.admin.entity.AdminSyncJobError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AdminSyncJobErrorRepository extends JpaRepository<AdminSyncJobError, Long> {
    List<AdminSyncJobError> findAllByJobIdInOrderByIdAsc(Collection<Long> jobIds);
    boolean existsByJobId(Long jobId);
}
