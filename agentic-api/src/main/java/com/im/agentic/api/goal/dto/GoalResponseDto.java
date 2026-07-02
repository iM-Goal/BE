package com.im.agentic.api.goal.dto;

import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalSlotType;
import com.im.agentic.domain.goal.entity.GoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class GoalResponseDto {

    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(description = "저축 목표 상세 정보 응답 DTO")
    public static class GoalResponse {
        @Schema(description = "목표 ID", example = "1")
        private Long id;

        @Schema(description = "구매 목표 아이템 이름", example = "나이키 에어맥스")
        private String itemName;

        @Schema(description = "목표 가격 (원화)", example = "128000")
        private Long targetAmount;

        @Schema(description = "현재까지 모은 저축액", example = "35000")
        private Long currentAmount;

        @Schema(description = "저축 목표 달성률 (퍼센트)", example = "27.3")
        private Double achievementRate;

        @Schema(description = "저축 슬롯 종류 (SHORT/MID/LONG)", example = "SHORT")
        private GoalSlotType slotType;

        @Schema(description = "목표 진행 상태 (ACTIVE/COMPLETED/CANCELLED)", example = "ACTIVE")
        private GoalStatus status;

        @Schema(description = "달성 예상일", example = "2026-07-21")
        private LocalDate estimatedCompletionDate;

        @Schema(description = "목표 달성까지 남은 일수", example = "21")
        private Long daysLeft;

        @Schema(description = "출금 잠금 조건 (JSON 형식)", example = "{\"conditions\":[{\"type\":\"DATE\",\"value\":\"2026-06-19\"}]}")
        private String withdrawalCondition;

        @Schema(description = "하루 소비 가용 예산", example = "50000")
        private Long dailyBudget;

        @Schema(description = "목표 기간", example = "30")
        private Integer durationDays;

        public static GoalResponse from(Goal goal) {
            Long daysLeft = null;
            if (goal.getEstimatedCompletionDate() != null) {
                daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), goal.getEstimatedCompletionDate());
                if (daysLeft < 0) daysLeft = 0L;
            }
            return GoalResponse.builder()
                    .id(goal.getId())
                    .itemName(goal.getItemName())
                    .targetAmount(goal.getTargetAmount())
                    .currentAmount(goal.getCurrentAmount())
                    .achievementRate(goal.getAchievementRate())
                    .slotType(goal.getSlotType())
                    .status(goal.getStatus())
                    .estimatedCompletionDate(goal.getEstimatedCompletionDate())
                    .daysLeft(daysLeft)
                    .withdrawalCondition(goal.getWithdrawalCondition())
                    .dailyBudget(goal.getDailyBudget())
                    .durationDays(goal.getDurationDays())
                    .build();
        }
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(description = "3슬롯 저축 목표 목록 응답 DTO")
    public static class GoalListResponse {
        @Schema(description = "단기 목표 정보 (30일)")
        private GoalResponse shortGoal;

        @Schema(description = "중기 목표 정보 (90일)")
        private GoalResponse midGoal;

        @Schema(description = "장기 목표 정보 (365일)")
        private GoalResponse longGoal;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(description = "목표 진행률 응답 DTO")
    public static class ProgressResponse {
        @Schema(description = "현재까지 모은 저축액", example = "35000")
        private Long currentAmount;

        @Schema(description = "저축 목표 달성률 (퍼센트)", example = "27.3")
        private Double achievementRate;

        @Schema(description = "달성까지 남은 일수", example = "21")
        private Long daysLeft;

        @Schema(description = "달성 예상일", example = "2026-07-21")
        private LocalDate estimatedCompletionDate;

        public static ProgressResponse from(Goal goal) {
            Long daysLeft = null;
            if (goal.getEstimatedCompletionDate() != null) {
                daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), goal.getEstimatedCompletionDate());
                if (daysLeft < 0) daysLeft = 0L;
            }
            return ProgressResponse.builder()
                    .currentAmount(goal.getCurrentAmount())
                    .achievementRate(goal.getAchievementRate())
                    .daysLeft(daysLeft)
                    .estimatedCompletionDate(goal.getEstimatedCompletionDate())
                    .build();
        }
    }

    @Getter 
    @Builder 
    @AllArgsConstructor
    @Schema(description = "Buy-out 출금 완료 응답 DTO")
    public static class BuyOutResponse {
        @Schema(description = "출금 완료된 목표 ID", example = "1")
        private Long goalId;

        @Schema(description = "목표 아이템 이름", example = "나이키 에어맥스")
        private String itemName;

        @Schema(description = "총 출금 저축액 (원화)", example = "128000")
        private Long totalSavedAmount;

        @Schema(description = "출금 완료 메시지", example = "목표 달성! 128000 iMKRW 소각 완료. DAGAON 지갑으로 128000원 출금. 나이키 에어맥스을(를) 구매하세요!")
        private String message;
    }
}
