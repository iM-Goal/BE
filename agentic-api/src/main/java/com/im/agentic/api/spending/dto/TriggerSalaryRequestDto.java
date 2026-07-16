package com.im.agentic.api.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TriggerSalaryRequestDto(
        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "입금된 월급 금액 (원)", example = "3000000")
        Long salaryAmount,

        @Schema(description = "저축 비율 (%)", example = "20")
        Integer savingRatio
) {}
