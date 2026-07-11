package com.im.agentic.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class ChatResponseDto {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ChatResponse {
        private String answer;
    }
}
