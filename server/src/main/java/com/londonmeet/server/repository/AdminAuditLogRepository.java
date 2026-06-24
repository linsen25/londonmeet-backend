package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    Optional<AdminAuditLog> findFirstByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, Long targetId
    );
}
