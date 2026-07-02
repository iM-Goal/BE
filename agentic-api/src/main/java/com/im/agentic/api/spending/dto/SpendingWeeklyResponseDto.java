package com.im.agentic.api.spending.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SpendingWeeklyResponseDto {
    private Long totalSpend;
    private List<CategoryWeeklyReport> categories;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategoryWeeklyReport {
        private String category;
        private Long amount;
        private Double vsLastWeek; // %
    }
}
