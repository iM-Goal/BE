package com.im.agentic.infra.agent.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.Map;

@Getter
@Builder
public class SpendingAnalysisOutput {
    private Map<String, Long> categoryExpenses;
    private Long totalExpenses;
    private Long dailyAvailableAmount;
    private String topCategory;
}
