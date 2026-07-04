package com.im.agentic.api.mydata.dto;

public record UserFinancialResponse(
    Long monthlyIncome,
    Long monthlyFixedCost,
    Long disposableIncome,
    Long dailyAvailable,
    Integer incomeQuintile,
    String quintileSource
) {}
