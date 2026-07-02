package com.im.agentic.api.spending.controller;

import com.im.agentic.api.spending.dto.SpendingTodayResponseDto;
import com.im.agentic.api.spending.dto.SpendingWeeklyResponseDto;
import com.im.agentic.api.spending.service.SpendingService;
import com.im.agentic.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spending")
@RequiredArgsConstructor
@Tag(name = "Spending", description = "소비 추적 및 가용 금액 모니터링 API")
public class SpendingController {

    private final SpendingService spendingService;

    @Operation(summary = "오늘 소비 현황", description = "오늘 사용한 금액, 잔여 가용 금액, 사용률을 반환합니다. 페이스메이커의 핵심 데이터입니다.")
    @GetMapping("/today")
    public ApiResponse<SpendingTodayResponseDto> getToday(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(spendingService.getToday(userId));
    }

    @Operation(summary = "주간 소비 리포트", description = "이번 주 카테고리별 소비를 4주 평균과 비교한 리포트를 반환합니다.")
    @GetMapping("/weekly")
    public ApiResponse<SpendingWeeklyResponseDto> getWeekly(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(spendingService.getWeekly(userId));
    }
}
