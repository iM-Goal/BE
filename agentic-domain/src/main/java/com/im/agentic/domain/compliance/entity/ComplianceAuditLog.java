package com.im.agentic.domain.compliance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_audit_logs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ComplianceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "plan_type")
    private String planType;

    @Column(name = "input_text", columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "recommended_product")
    private String recommendedProduct;

    @Column(name = "is_compliant", nullable = false)
    private Boolean isCompliant;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "requires_human_review", nullable = false)
    private Boolean requiresHumanReview;

    @Column(name = "human_review_reason", columnDefinition = "TEXT")
    private String humanReviewReason;

    @Column(name = "matched_rules", columnDefinition = "TEXT")
    private String matchedRules;

    @Column(name = "compliance_footnote", columnDefinition = "TEXT")
    private String complianceFootnote;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
