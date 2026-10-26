package com.im.agentic.api.planning.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlanReportRequestDto(
        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "자연어 목표 입력", example = "1년 동안 천만원 모아서 종잣돈 만들고 싶어")
        String rawGoalInput,

        @Schema(description = "희망 목표 금액(원) - 미입력 시 AI 추출", example = "10000000")
        Long targetAmount,

        @Schema(description = "희망 기간(일) - 미입력 시 AI 추출", example = "365")
        Integer durationDays
) {}
