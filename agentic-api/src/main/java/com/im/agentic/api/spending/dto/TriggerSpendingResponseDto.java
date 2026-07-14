package com.im.agentic.api.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TriggerSpendingResponseDto(
        @Schema(description = "처리 성공 여부")
        boolean success,
        
        @Schema(description = "알림 발생 여부 (과소비 감지 시 true)")
        boolean hasNotification,
        
        @Schema(description = "알림 메시지 텍스트")
        String notificationMessage
) {}
