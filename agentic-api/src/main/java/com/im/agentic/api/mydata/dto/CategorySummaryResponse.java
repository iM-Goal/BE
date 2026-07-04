package com.im.agentic.api.mydata.dto;

import java.util.Map;

public record CategorySummaryResponse(
    String userId,
    String weekStart,                    // 이번 주 시작일 yyyy-MM-dd
    Map<String, Long> thisWeek,          // 카테고리별 이번 주 합계
    Map<String, Long> baseline,          // 카테고리별 4주 평균
    Map<String, Double> overageRate,     // 초과율 % (thisWeek-baseline)/baseline*100
    Integer incomeQuintile,
    Double overageThresholdPct           // 소득분위별 임계치
) {}
