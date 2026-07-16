package com.im.agentic.api.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "월급 입금 → 목표 자동이체 응답 DTO")
public class TriggerSalaryResponseDto {

    @Schema(description = "처리 성공 여부")
    private boolean success;

    @Schema(description = "총 저축 금액 (salaryAmount × savingRatio%)", example = "600000")
    private long totalSavingAmount;

    @Schema(description = "목표별 비례 이체 내역 리스트")
    private List<GoalAllocation> allocations;

    @Getter
    @Builder
    @Schema(description = "목표별 이체 내역")
    public static class GoalAllocation {

        @Schema(description = "목표 ID", example = "1")
        private Long goalId;

        @Schema(description = "목표 상품명", example = "에어팟 프로")
        private String goalName;

        @Schema(description = "목표 금액 (비율 계산 기준)", example = "300000")
        private Long targetAmount;

        @Schema(description = "이번에 이체된 금액", example = "138462")
        private Long transferredAmount;

        @Schema(description = "이체 후 누적 저축액", example = "438462")
        private Long currentAmount;

        @Schema(description = "이체 후 달성률 (%)", example = "46.2")
        private Double achievementRate;
    }
}
