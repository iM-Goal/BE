package com.im.agentic.api.mydata.dto;

import jakarta.validation.constraints.Min;

public record UserFinancialRequest(
    @Min(0) Long monthlyIncome,
    @Min(0) Long monthlyFixedCost
) {}
