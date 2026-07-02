package com.im.agentic.api.goal.controller;

import com.im.agentic.api.goal.dto.GoalRequestDto.*;
import com.im.agentic.api.goal.dto.GoalResponseDto.*;
import com.im.agentic.api.goal.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.im.agentic.common.response.ApiResponse.success;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Tag(name = "Goal", description = "저축 목표 등록/조회/수정/삭제 및 Buy-out API")
public class GoalController {

    private final GoalService goalService;

    @Operation(
        summary = "목표 등록",
        description = """
            저축 목표를 등록합니다.
            
            **슬롯 종류 (slotType)**
            - `SHORT`: 단기 목표 (30일)
            - `MID`: 중기 목표 (90일)
            - `LONG`: 장기 목표 (365일)
            
            **등록 방식 (registrationType)**
            - `NATURAL_LANGUAGE`: 자연어 입력 → GoalParsingAgent가 자동으로 상품명/가격 파싱
            - `MANUAL`: 직접 입력
            
            **AI 가용 잔액 역산 설계**
            목표 등록 시 입력되는 `durationDays`(기간), `monthlyIncome`(월 소득), `fixedExpense`(월 고정 지출) 파라미터를 기반으로 AI가 자동으로 하루 가용 예산(`dailyBudget = (monthlyIncome - fixedExpense) / durationDays`)을 계산해 줍니다.
            
            **자연어 등록 시 rawInput 필드를 채우면 AI가 자동 파싱합니다.**
            예: "나이키 에어맥스 사고싶어" → itemName: "나이키 에어맥스", price: 128000
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                examples = {
                    @ExampleObject(
                        name = "자연어 등록 (AI 파싱)",
                        value = """
                            {
                                "slotType": "SHORT",
                                "registrationType": "NATURAL_LANGUAGE",
                                "rawInput": "나이키 에어맥스 사고싶어"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "직접 입력",
                        value = """
                            {
                                "slotType": "MID",
                                "registrationType": "MANUAL",
                                "itemName": "아이패드 프로",
                                "targetAmount": 1299000,
                                "itemImageUrl": "https://example.com/ipad.jpg"
                            }
                            """
                    )
                }
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "목표 등록 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "id": 1,
                        "itemName": "나이키 에어맥스",
                        "targetAmount": 128000,
                        "currentAmount": 0,
                        "achievementRate": 0.0,
                        "slotType": "SHORT",
                        "status": "ACTIVE",
                        "estimatedCompletionDate": "2026-07-21"
                    }
                }
                """))
        ),
        @ApiResponse(responseCode = "409", description = "해당 슬롯에 이미 활성 목표 존재"),
        @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public com.im.agentic.common.response.ApiResponse<GoalResponse> createGoal(
            @AuthenticationPrincipal Long userId,
            @RequestBody CreateGoalRequest request) {
        return success(goalService.createGoal(userId, request));
    }

    @Operation(summary = "목표 목록 조회",
        description = """
            사용자의 모든 활성 목표를 슬롯별로 조회합니다.
            
            SHORT / MID / LONG 슬롯 각 1개씩, 없는 슬롯은 null 반환.
            """)
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "shortGoal": {
                            "id": 1,
                            "itemName": "나이키 에어맥스",
                            "targetAmount": 128000,
                            "currentAmount": 35000,
                            "achievementRate": 27.3,
                            "slotType": "SHORT",
                            "status": "ACTIVE"
                        },
                        "midGoal": null,
                        "longGoal": null
                    }
                }
                """))
        )
    })
    @GetMapping
    public com.im.agentic.common.response.ApiResponse<GoalListResponse> getGoals(@AuthenticationPrincipal Long userId) {
        return success(goalService.getGoals(userId));
    }

    @Operation(summary = "목표 단건 조회", description = "goalId로 특정 목표를 조회합니다.")
    @GetMapping("/{goalId}")
    public com.im.agentic.common.response.ApiResponse<GoalResponse> getGoal(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "목표 ID", example = "1") @PathVariable Long goalId) {
        return success(goalService.getGoal(userId, goalId));
    }

    @Operation(summary = "목표 수정", description = "목표의 아이템명, 목표금액, 이미지를 수정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "401", description = "타인의 목표 수정 불가")
    })
    @PatchMapping("/{goalId}")
    public com.im.agentic.common.response.ApiResponse<GoalResponse> updateGoal(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "목표 ID", example = "1") @PathVariable Long goalId,
            @RequestBody UpdateGoalRequest request) {
        return success(goalService.updateGoal(userId, goalId, request));
    }

    @Operation(summary = "목표 삭제", description = "목표를 취소(CANCELLED) 상태로 변경합니다. 실제 삭제는 아닙니다.")
    @DeleteMapping("/{goalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public com.im.agentic.common.response.ApiResponse<Void> deleteGoal(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "목표 ID", example = "1") @PathVariable Long goalId) {
        goalService.deleteGoal(userId, goalId);
        return success(null);
    }

    @Operation(summary = "목표 진행률 조회", description = "특정 목표의 저축 진행률과 남은 기간을 조회합니다.")
    @GetMapping("/{goalId}/progress")
    public com.im.agentic.common.response.ApiResponse<ProgressResponse> getGoalProgress(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "목표 ID", example = "1") @PathVariable Long goalId) {
        return success(goalService.getGoalProgress(userId, goalId));
    }

    @Operation(
        summary = "Buy-out (목표 달성 출금)",
        description = """
            목표 달성 후 저축금을 출금합니다.
            
            **Buy-out 플로우 (자동 처리)**
            1. Kaia 스마트컨트랙트 잠금 해제
            2. iMKRW 토큰 소각
            3. DAGAON 디지털 지갑으로 원화 출금
            
            **출금 조건이 설정된 경우 조건 미충족 시 출금이 제한될 수 있습니다.**
            """)
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Buy-out 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "goalId": 1,
                        "itemName": "나이키 에어맥스",
                        "totalSavedAmount": 128000,
                        "message": "목표 달성! 128000 iMKRW 소각 완료. DAGAON 지갑으로 128000원 출금. 나이키 에어맥스을(를) 구매하세요!"
                    }
                }
                """))
        )
    })
    @PostMapping("/{goalId}/buyout")
    public com.im.agentic.common.response.ApiResponse<BuyOutResponse> buyOut(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "목표 ID", example = "1") @PathVariable Long goalId) {
        return success(goalService.buyOut(userId, goalId));
    }
}

