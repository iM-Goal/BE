package com.im.agentic.api.mydata.controller;

import com.im.agentic.api.mydata.dto.CardApprovalRequest;
import com.im.agentic.api.mydata.dto.CategorySummaryResponse;
import com.im.agentic.api.mydata.dto.MyDataCacheResponseDto;
import com.im.agentic.api.mydata.dto.ThresholdResponse;
import com.im.agentic.api.mydata.service.MyDataService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.im.agentic.common.response.ApiResponse.success;

@RestController
@RequestMapping("/api/mydata")
@RequiredArgsConstructor
@Tag(name = "MyData", description = "마이데이터 기반 소비 분석 및 캐시 조회 API")
public class MyDataController {

    private final MyDataService myDataService;

    @Operation(
        summary = "마이데이터 분석 실행",
        description = """
            마이데이터 연동 후 소비 내역을 분석합니다.
            
            **SpendingAnalysisAgent (8002)** 가 실행됩니다.
            1. 오픈뱅킹 API에서 최근 30일 소비 내역 수집
            2. 카테고리별 지출 집계
            3. 소비 내역을 nomic-embed-text로 임베딩 → pgvector에 저장
            4. 일일 가용 금액 계산 후 캐시 저장
            
            **미션 생성 전에 반드시 1회 이상 실행해야 합니다.**
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "분석 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "categoryExpenses": {
                            "카페": 40000,
                            "식비": 80000,
                            "교통": 30000,
                            "편의점": 15000
                        },
                        "dailyAvailableAmount": 95000,
                        "lastUpdatedAt": "2026-06-19T23:00:00"
                    }
                }
                """))
        )
    })
    @PostMapping("/analyze")
    public com.im.agentic.common.response.ApiResponse<MyDataCacheResponseDto> analyzeAndCache(
            @AuthenticationPrincipal Long userId) {
        return success(myDataService.analyzeAndCache(userId));
    }

    @Operation(
        summary = "마이데이터 캐시 조회",
        description = """
            가장 최근에 분석된 마이데이터 캐시를 반환합니다.
            
            캐시가 없는 경우 자동으로 분석을 실행합니다.
            """
    )
    @GetMapping
    public com.im.agentic.common.response.ApiResponse<MyDataCacheResponseDto> getCache(
            @AuthenticationPrincipal Long userId) {
        return success(myDataService.getCache(userId));
    }

    @Operation(summary = "카드 승인 내역 수집", description = "금융보안원 마이데이터 API 규격을 기반으로 승인번호, 승인일시, 금액, 가맹점명을 전달받아 카테고리 매핑과 함께 일괄 저장합니다.")
    @PostMapping("/card-approvals")
    public com.im.agentic.common.response.ApiResponse<Void> importCardApprovals(
            @AuthenticationPrincipal Long userId,
            @RequestBody List<CardApprovalRequest> approvals) {
        myDataService.importCardApprovals(userId, approvals);
        return success(null);
    }

    @Operation(summary = "카테고리별 지출 요약 조회", description = "이번 주 카테고리별 지출 합계와 4주 지출 baseline 평균, 소득분위별 초과 임계치를 집계하여 반환합니다.")
    @GetMapping("/category-summary")
    public com.im.agentic.common.response.ApiResponse<CategorySummaryResponse> getCategorySummary(
            @AuthenticationPrincipal Long userId) {
        return success(myDataService.getCategorySummary(userId));
    }

    @Operation(summary = "소득분위별 과소비 임계치 조회", description = "통계청 가계동향조사 데이터에 따라 유저의 소득 분위별 과소비 임계치를 조회합니다.")
    @GetMapping("/threshold")
    public com.im.agentic.common.response.ApiResponse<ThresholdResponse> getThreshold(
            @AuthenticationPrincipal Long userId) {
        return success(myDataService.getThreshold(userId));
    }
}
