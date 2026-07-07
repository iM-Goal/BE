package com.im.agentic.api.deposit.controller;

import com.im.agentic.api.deposit.dto.DepositResponseDto;
import com.im.agentic.api.deposit.dto.WithdrawPreviewResponseDto;
import com.im.agentic.api.deposit.service.DepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.im.agentic.common.response.ApiResponse.success;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
@Tag(name = "Deposit", description = "보증금(iMKRW 잠금) 관리 및 출금 마찰 플로우 API")
public class DepositController {

    private final DepositService depositService;

    @Operation(summary = "보증금 현황 조회", description = "사용자의 전체 보증금 목록과 잠금/해제/출금 상태를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": [
                        {
                            "id": 1,
                            "missionId": 3,
                            "userId": 42,
                            "amount": 15000,
                            "imkrwAmount": 15000,
                            "status": "LOCKED",
                            "lockTxHash": "0xlock123abc",
                            "releaseTxHash": null,
                            "lockedAt": "2026-06-30T10:00:00",
                            "releasedAt": null
                        }
                    ]
                }
                """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음")
    })
    @GetMapping
    public com.im.agentic.common.response.ApiResponse<List<DepositResponseDto>> getDeposits(
            @AuthenticationPrincipal Long userId) {
        return success(depositService.getDeposits(userId));
    }

    @Operation(summary = "출금 미리보기", description = "출금 시 목표 달성이 며칠 늦어지는지 계산하고, 5분간 유효한 확인 코드를 발급합니다. 이것이 출금 마찰 플로우의 1단계입니다.")
    @GetMapping("/{id}/withdraw-preview")
    public com.im.agentic.common.response.ApiResponse<WithdrawPreviewResponseDto> getWithdrawPreview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return success(depositService.getWithdrawPreview(userId, id));
    }

    @Operation(summary = "보증금 출금 실행", description = "확인 코드 검증 후 iMKRW를 소각하고 원화로 출금합니다. 강제 차단이 아닌 마찰을 통한 충동 출금 방지 장치입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "출금 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "확인 코드 불일치 (WRONG_CONFIRM_CODE)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "확인 코드 만료, 5분 초과 (CONFIRM_CODE_EXPIRED)")
    })
    @PostMapping("/{id}/withdraw")
    public com.im.agentic.common.response.ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody WithdrawRequest request) {
        depositService.withdraw(userId, id, request.getReason(), request.getConfirmCode());
        return success(null);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(description = "보증금 중도 인출 확인 요청")
    public static class WithdrawRequest {
        @Schema(description = "중도 인출 사유", example = "급전 필요")
        private String reason;
        @Schema(description = "출금 미리보기에서 발급받은 4자리 확인 코드", example = "8472")
        private String confirmCode;
    }
}
