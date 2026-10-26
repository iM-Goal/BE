package com.im.agentic.infra.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceInspectOutput {
    private boolean isCompliant;
    private String riskLevel;
    private boolean requiresHumanReview;
    private String reviewReason;
    private List<String> matchedRules;
    private String complianceFootnote;
}
