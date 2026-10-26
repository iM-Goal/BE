package com.im.agentic.domain.compliance.repository;

import com.im.agentic.domain.compliance.entity.ComplianceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplianceRuleRepository extends JpaRepository<ComplianceRule, Long> {
    List<ComplianceRule> findByRuleType(String ruleType);
    List<ComplianceRule> findByLawName(String lawName);
}
