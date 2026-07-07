package com.im.agentic.api.mydata.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ThresholdResponse(
    @Schema(description = "소득 분위 (1분위=저소득 ~ 5분위=고소득)", example = "3")
    Integer incomeQuintile,
    @Schema(description = "과소비 임계치 배율 (ex: 1.4 = 평균 대비 140% 초과 시 과소비)", example = "1.4")
    Double overageThresholdPct,
    @Schema(description = "처분가능소득 (월소득 - 고정지출, 원)", example = "3300000")
    Long disposableIncome,
    @Schema(description = "일일 가용 금액 (처분가능소득 / 30, 원)", example = "110000")
    Long dailyAvailable,
    @Schema(description = "데이터 출처", example = "통계청 2024 Q4 가계동향조사")
    String source,
    @Schema(description = "임계치 설명 메모", example = "3분위 기준, 평균 대비 140% 초과 시 과소비로 판정")
    String note
) {}
