package com.im.agentic.api.mydata.controller;

import com.im.agentic.api.mydata.dto.UserFinancialRequest;
import com.im.agentic.api.mydata.dto.UserFinancialResponse;
import com.im.agentic.api.mydata.service.UserFinancialService;
import com.im.agentic.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.im.agentic.common.response.ApiResponse.success;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Financial", description = "유저 개인 재정 정보 관리 API")
public class UserFinancialController {

    private final UserFinancialService userFinancialService;

    @Operation(summary = "유저 재정 정보 등록 및 수정", description = "월 소득과 고정 지출을 입력받아 소득 분위 및 가용 금액을 계산하여 저장합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재정 정보 등록 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "monthlyIncome": 4500000,
                        "monthlyFixedCost": 1200000,
                        "disposableIncome": 3300000,
                        "dailyAvailable": 110000,
                        "incomeQuintile": 3,
                        "quintileSource": "통계청 2024 Q4 가계동향조사"
                    }
                }
                """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 유효성 오류 (음수 금액 등)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @PutMapping("/financial-info")
    public ApiResponse<UserFinancialResponse> update(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UserFinancialRequest req) {
        return success(userFinancialService.updateFinancialInfo(userId, req));
    }

    @Operation(summary = "유저 재정 정보 조회", description = "저장된 유저의 소득 분위 및 가용 금액 등의 재정 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "재정 정보 미등록 상태 (PUT /financial-info 먼저 호출 필요)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @GetMapping("/financial-info")
    public ApiResponse<UserFinancialResponse> get(
            @AuthenticationPrincipal Long userId) {
        return success(userFinancialService.getFinancialInfo(userId));
    }
}
