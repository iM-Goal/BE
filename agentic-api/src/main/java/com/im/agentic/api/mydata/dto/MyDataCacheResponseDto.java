package com.im.agentic.api.mydata.dto;

import com.im.agentic.domain.mydata.entity.MyDataCache;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MyDataCacheResponseDto {
    private String categoryExpenses;
    private String weeklyAvgExpenses;
    private Long dailyAvailableAmount;
    private LocalDateTime lastUpdatedAt;

    public static MyDataCacheResponseDto from(MyDataCache cache) {
        if (cache == null) return null;
        return MyDataCacheResponseDto.builder()
                .categoryExpenses(cache.getCategoryExpenses())
                .weeklyAvgExpenses(cache.getWeeklyAvgExpenses())
                .dailyAvailableAmount(cache.getDailyAvailableAmount())
                .lastUpdatedAt(cache.getLastUpdatedAt())
                .build();
    }
}
