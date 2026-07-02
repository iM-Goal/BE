package com.im.agentic.infra.agent.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MissionGenerationOutput {
    private String restraintCategory;
    private Long restraintLimitAmount;
    private Double restraintContributionRate;
    private Long savingTransferAmount;
    private Double savingContributionRate;
}
