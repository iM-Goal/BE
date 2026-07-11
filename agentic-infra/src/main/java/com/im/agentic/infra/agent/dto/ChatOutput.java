package com.im.agentic.infra.agent.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatOutput {
    private String answer;
}
