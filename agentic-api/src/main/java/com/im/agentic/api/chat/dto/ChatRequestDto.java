package com.im.agentic.api.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ChatRequestDto {

    @Getter
    @NoArgsConstructor
    public static class ChatRequest {
        @NotBlank(message = "메시지를 입력해주세요")
        @Schema(description = "사용자 질문", example = "이번 달 카페 소비가 너무 많은 것 같아")
        private String message;
    }
}
