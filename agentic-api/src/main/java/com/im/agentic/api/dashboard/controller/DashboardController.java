package com.im.agentic.api.dashboard.controller;

import com.im.agentic.api.dashboard.dto.DashboardResponseDto.DashboardResponse;
import com.im.agentic.api.dashboard.dto.DashboardResponseDto.SpendingGraphResponse;
import com.im.agentic.api.dashboard.dto.DashboardResponseDto.WeeklyReportResponse;
import com.im.agentic.api.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.im.agentic.common.response.ApiResponse.success;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "저축 현황 및 주간 리포트 API")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
        summary = "메인 대시보드 조회",
        description = "사용자의 현재 활성 목표들과 오늘의 미션 진행 현황, 총 저축 금액을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "totalSavedAmount": 35000,
                        "activeGoalCount": 1,
                        "todayMissionAchievementRate": 50.0
                    }
                }
                """))
        )
    })
    @GetMapping
    public com.im.agentic.common.response.ApiResponse<DashboardResponse> getDashboard(@AuthenticationPrincipal Long userId) {
        DashboardResponse response = dashboardService.getDashboard(userId);
        return success(response);
    }

    @Operation(
        summary = "소비 그래프 조회",
        description = "최근 지출 추이와 카테고리별 소비 데이터를 그래프 형태로 제공합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "dailyExpenses": [
                            {"date": "2026-06-13", "amount": 12000},
                            {"date": "2026-06-14", "amount": 8000}
                        ],
                        "categoryPercentages": [
                            {"category": "식비", "percentage": 45.5},
                            {"category": "카페", "percentage": 20.0}
                        ]
                    }
                }
                """))
        )
    })
    @GetMapping("/spending")
    public com.im.agentic.common.response.ApiResponse<SpendingGraphResponse> getSpendingGraph(@AuthenticationPrincipal Long userId) {
        SpendingGraphResponse response = dashboardService.getSpendingGraph(userId);
        return success(response);
    }

    @Operation(
        summary = "주간 리포트 조회",
        description = "AI 에이전트가 요약한 지난 한 주간의 저축/소비 분석 주간 리포트를 제공합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "weekRange": "2026-06-12 ~ 2026-06-18",
                        "totalSaved": 24000,
                        "savedChangeRate": 12.5,
                        "reportSummary": "이번 주는 커피 지출을 20% 줄이며 목표 금액 달성에 한 걸음 더 다가갔습니다. 아주 훌륭한 소비 절제 패턴입니다."
                    }
                }
                """))
        )
    })
    @GetMapping("/weekly-report")
    public com.im.agentic.common.response.ApiResponse<WeeklyReportResponse> getWeeklyReport(@AuthenticationPrincipal Long userId) {
        WeeklyReportResponse response = dashboardService.getWeeklyReport(userId);
        return success(response);
    }
}
