package com.im.agentic.api.dashboard.dto;

import com.im.agentic.api.goal.dto.GoalResponseDto;
import com.im.agentic.api.mission.dto.MissionResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

public class DashboardResponseDto {

    @Getter @Builder @AllArgsConstructor
    public static class DashboardResponse {
        private GoalResponseDto.GoalListResponse goals;
        private List<MissionResponseDto> todayMissions;
        private String weeklyAchievementRate;  // ex) "5/7"
        private Long totalSavedAmount;
    }

    @Getter @Builder @AllArgsConstructor
    public static class SpendingGraphResponse {
        private List<CategorySpending> categories;
        private Long totalAmount;
    }

    @Getter @Builder @AllArgsConstructor
    public static class CategorySpending {
        private String category;
        private Long amount;
        private Double percentage;
    }

    @Getter @Builder @AllArgsConstructor
    public static class WeeklyReportResponse {
        private String missionAchievementRate;  // ex) "5/7"
        private Long totalSavedAmount;           // 절약 금액 합산
        private Long totalSavingAmount;          // 이체 금액 합산
        private Long daysLeftChange;
        private String period;
    }
}
