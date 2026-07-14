package com.im.agentic.api.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TriggerSpendingRequestDto(
        @Schema(description = "사용자 ID", example = "1")
        Long userId,
        
        @Schema(description = "가맹점명", example = "스타벅스")
        String merchantName,
        
        @Schema(description = "결제 금액", example = "50000")
        Long amount,
        
        @Schema(description = "카테고리 (영문 Enum 값 기준: CAFE, FOOD, SHOPPING 등)", example = "CAFE")
        String category
) {}
