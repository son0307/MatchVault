package com.son.soccerStreaming.admin.repository;

import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @EntityGraph(attributePaths = "adminUser")
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
