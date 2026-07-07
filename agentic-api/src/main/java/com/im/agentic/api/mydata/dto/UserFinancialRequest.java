package com.im.agentic.api.mydata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record UserFinancialRequest(
    @Schema(description = "월 소득 (원)", example = "4500000") @Min(0) Long monthlyIncome,
    @Schema(description = "월 고정 지출 (원, 월세·보험·구독료 등)", example = "1200000") @Min(0) Long monthlyFixedCost
) {}
