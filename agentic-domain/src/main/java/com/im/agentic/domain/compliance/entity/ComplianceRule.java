package com.im.agentic.domain.compliance.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "compliance_rules")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ComplianceRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "law_name", nullable = false)
    private String lawName;

    @Column(name = "article_no", nullable = false)
    private String articleNo;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "requires_human_review")
    private Boolean requiresHumanReview;
}
