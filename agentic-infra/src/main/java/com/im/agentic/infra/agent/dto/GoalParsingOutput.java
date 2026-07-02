package com.im.agentic.infra.agent.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalParsingOutput {
    private String itemName;
    private Long price;
    private String category;
}
