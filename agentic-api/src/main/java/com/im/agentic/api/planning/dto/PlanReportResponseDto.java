package com.im.agentic.api.planning.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "맞춤형 2트랙 계획서 레포트 응답 DTO")
public class PlanReportResponseDto {

    @Schema(description = "플랜 유형: CONSUMPTION(일반 소비형) 또는 ASSET_ACCUMULATION(목돈 자산형성형)", example = "ASSET_ACCUMULATION")
    private String planType;

    @Schema(description = "목표 개요 정보")
    private GoalOverview goalOverview;

    @Schema(description = "소득 기반 월간 저축 여력 분석")
    private IncomeSavingCapacity incomeSavingCapacity;

    @Schema(description = "일일 가용 예산 지표 (dailyAvailable 기준)")
    private DailyBudgetMetric dailyBudgetMetric;

    @Schema(description = "iM 금융 상품 연계 (소비형일 경우 null 또는 안내만 표기)")
    private FinancialProductRecommendation financialProduct;

    @Schema(description = "소비 패턴 분석 및 게이미피케이션")
    private SpendingPatternAnalysis spendingAnalysis;

    @Schema(description = "레포트 하단 내부통제 근거 각주 (금소법 적합성 등)")
    private String complianceFootnote;

    @Schema(description = "전문 상담원(담당자) 1:1 확인 필요 여부 (불완전판매 방지용)", example = "false")
    private boolean requiresHumanReview;

    @Schema(description = "담당자 확인 필요 사유 (해당 시)", example = "월 저축액이 가처분소득의 50%를 초과하여 생활비 부족 리스크가 감지되었습니다.")
    private String humanReviewReason;

    @Getter
    @Builder
    @Schema(description = "목표 개요")
    public static class GoalOverview {
        private String goalName;
        private Long targetAmount;
        private Integer durationDays;
        private String estimatedCompletionDate;
    }

    @Getter
    @Builder
    @Schema(description = "소득 기반 월간 저축 여력")
    public static class IncomeSavingCapacity {
        private Long monthlyIncome;
        private Long fixedExpense;
        private Long disposableIncome;
        private Long recommendedMonthlySavings;
        private Double savingCapacityRatio; // %
    }

    @Getter
    @Builder
    @Schema(description = "하루 가용 예산 지표")
    public static class DailyBudgetMetric {
        private Long normalDailyAvailable;     // 평소 하루 가용예산
        private Long dailySavingTarget;         // 목표를 위한 하루 필요 저축액
        private Long adjustedDailyBudget;       // 앞으로 쓸 수 있는 새로운 하루 생활비
        private String difficultyLevel;         // 안정, 보통, 도전
    }

    @Getter
    @Builder
    @Schema(description = "iM 금융상품 연계 추천")
    public static class FinancialProductRecommendation {
        private String productName;
        private String maxInterestRate;
        private String baseInterestRate;
        private Long monthlyDeposit;
        private Long expectedMaturityAmount;
        private Long expectedInterestBenefit;
        private Integer daysShortened;
        private String actionButtonText;
    }

    @Getter
    @Builder
    @Schema(description = "소비 패턴 분석 및 게이미피케이션")
    public static class SpendingPatternAnalysis {
        private String characterType;          // 예: 야행성 배달 올빼미 🦉
        private String characterDescription;
        private String bossMonsterName;         // 예: 월 38만 원 배달 몬스터
        private String questDescription;
        private List<String> actionSolutions;
        private String rewardBadge;             // 예: FCPS +18점 & 완등 티켓 발권
    }
}
