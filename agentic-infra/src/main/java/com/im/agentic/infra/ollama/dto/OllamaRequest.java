package com.im.agentic.infra.ollama.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OllamaRequest {
    private String model;
    private String prompt;
    private boolean stream;
}
