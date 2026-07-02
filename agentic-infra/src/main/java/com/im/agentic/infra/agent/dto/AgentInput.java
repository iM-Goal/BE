package com.im.agentic.infra.agent.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentInput {
    private Long userId;
    private Long goalId;
    private String rawInput;           // 자연어 목표 입력
    private String categoryExpenses;   // 소비 카테고리 JSON
    private Long dailyAvailableAmount;
    private String goalItemName;
}
