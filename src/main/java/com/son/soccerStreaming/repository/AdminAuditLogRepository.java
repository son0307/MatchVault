package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @EntityGraph(attributePaths = "adminUser")
    List<AdminAuditLog> findTop50ByOrderByCreatedAtDesc();
}
