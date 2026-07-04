package com.im.agentic.api.mydata.dto;

public record ThresholdResponse(
    Integer incomeQuintile,
    Double overageThresholdPct,
    Long disposableIncome,
    Long dailyAvailable,
    String source,
    String note
) {}
