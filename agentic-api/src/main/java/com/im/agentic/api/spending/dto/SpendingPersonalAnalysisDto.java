package com.im.agentic.api.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "사용자 본인 데이터 기반 개인화 소비 분석 응답")
public class SpendingPersonalAnalysisDto {

    // ── 재정 기반 정보 (User 엔티티) ──
    @Schema(description = "월 소득 (원)", example = "4500000")
    private Long monthlyIncome;

    @Schema(description = "월 고정 지출 (원, 월세·보험·구독료 등)", example = "1200000")
    private Long fixedExpense;

    @Schema(description = "처분가능소득 (월소득 - 고정지출, 원)", example = "3300000")
    private Long disposableIncome;

    @Schema(description = "일일 가용 예산 (처분가능소득 / 30, 원)", example = "110000")
    private Long dailyBudget;

    // ── 오늘 소비 현황 ──
    @Schema(description = "오늘 사용한 금액 (원)", example = "43000")
    private Long todaySpend;

    @Schema(description = "오늘 잔여 가용 금액 (원)", example = "67000")
    private Long todayRemaining;

    @Schema(description = "오늘 가용 예산 대비 사용률 (%)", example = "39.1")
    private Double todayUsageRate;

    // ── 이번 주 카테고리별 분석 (본인 4주 평균 기준) ──
    @Schema(description = "이번 주 총 지출 (원)", example = "287000")
    private Long weeklyTotalSpend;

    @Schema(description = "이번 주 예산 대비 사용률 (주간 가용 예산 = dailyBudget × 7, %)", example = "37.3")
    private Double weeklyBudgetUsageRate;

    @Schema(description = "카테고리별 개인화 분석 리스트")
    private List<CategoryPersonalReport> categories;

    // ── 위험 신호 ──
    @Schema(description = "본인 평균 대비 과소비 카테고리 목록 (초과율 20% 이상)", example = "[\"카페\", \"배달음식\"]")
    private List<String> overSpendingCategories;

    @Schema(description = "이번 달 남은 일수 기준 예상 초과 여부", example = "true")
    private Boolean projectedOverBudget;

    @Schema(description = "이번 달 예상 총 지출 (현재 추세 유지 시, 원)", example = "3650000")
    private Long projectedMonthlySpend;

    // ── 내부 DTO ──
    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(description = "카테고리별 개인화 소비 분석")
    public static class CategoryPersonalReport {
        @Schema(description = "카테고리명", example = "카페")
        private String category;

        @Schema(description = "이번 주 지출 (원)", example = "42000")
        private Long thisWeekAmount;

        @Schema(description = "본인 4주 평균 주간 지출 (원)", example = "28000")
        private Long myWeeklyAvg;

        @Schema(description = "본인 평균 대비 초과율 (%, 음수=절약)", example = "50.0")
        private Double vsMyAvgRate;

        @Schema(description = "이번 주 지출이 일일 가용 예산의 몇 %인지", example = "54.5")
        private Double pctOfDailyBudget;

        @Schema(description = "과소비 여부 (본인 평균 대비 20% 이상 초과)", example = "true")
        private Boolean isOverSpending;
    }
}
