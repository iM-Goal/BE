package com.im.agentic.api.spending.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SpendingTodayResponseDto {
    private Long dailyBudget;
    private Long todaySpend;
    private Long remainingBudget;
    private Double usageRate;
}
