package com.im.agentic.infra.agent.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExecutionOutput {
    private boolean success;
    private boolean shouldTransfer;
    private Long savedAmount;
    private String message;
}
