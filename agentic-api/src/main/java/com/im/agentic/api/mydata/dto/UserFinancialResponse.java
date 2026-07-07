package com.im.agentic.api.mydata.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserFinancialResponse(
    @Schema(description = "월 소득 (원)", example = "4500000")
    Long monthlyIncome,
    @Schema(description = "월 고정 지출 (원)", example = "1200000")
    Long monthlyFixedCost,
    @Schema(description = "처분가능소득 (원)", example = "3300000")
    Long disposableIncome,
    @Schema(description = "일일 가용 금액 (원)", example = "110000")
    Long dailyAvailable,
    @Schema(description = "소득 분위 (1~5)", example = "3")
    Integer incomeQuintile,
    @Schema(description = "소득분위 산정 기준 출처", example = "통계청 2024 Q4 가계동향조사")
    String quintileSource
) {}
