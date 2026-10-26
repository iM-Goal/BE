package com.im.agentic.domain.compliance.repository;

import com.im.agentic.domain.compliance.entity.ComplianceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplianceAuditLogRepository extends JpaRepository<ComplianceAuditLog, Long> {
    List<ComplianceAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ComplianceAuditLog> findByRequiresHumanReviewTrueOrderByCreatedAtDesc();
}
