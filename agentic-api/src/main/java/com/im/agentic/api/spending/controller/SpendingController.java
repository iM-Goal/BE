package com.im.agentic.api.spending.controller;

import com.im.agentic.api.spending.dto.SpendingPersonalAnalysisDto;
import com.im.agentic.api.spending.dto.SpendingTodayResponseDto;
import com.im.agentic.api.spending.dto.SpendingWeeklyResponseDto;
import com.im.agentic.api.spending.service.SpendingService;
import com.im.agentic.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(
        summary = "개인화 소비 분석",
        description = """
            통계청 소득분위 기준이 아니라, \n해당 사용자의 \uac1c인 재정 데이터(월소득, 고정지출, 처분가능소득)와 \n본인의 4주 평균 소비 패턴을 기준으로 분석합니다.
            
            **포함 데이터**
            - 재정 기반: 월소득, 고정지출, 처분가능소득, 일일 가용 예산
            - 오늘 소비: 사용 금액 / 잔여 예산 / 사용률
            - 주간 카테고리별: 이번 주 실제 지출 vs **내 4주 평균** (인구 평균과 비교 안 함)
            - 과소비 위험 카테고리: 본인 평균 대비 20% 이상 초과
            - 월간 초과 예상: 현재 추세 유지 시 이번 달 예상 지출
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "분석 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "재정 정보 미등록 (PUT /api/users/financial-info 먼저 호출 필요)")
    })
    @GetMapping("/personal-analysis")
    public ApiResponse<SpendingPersonalAnalysisDto> getPersonalAnalysis(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(spendingService.getPersonalAnalysis(userId));
    }
}
