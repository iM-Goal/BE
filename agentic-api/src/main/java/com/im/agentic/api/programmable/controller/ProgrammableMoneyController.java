package com.im.agentic.api.programmable.controller;

import com.im.agentic.common.response.ApiResponse;
import com.im.agentic.infra.blockchain.StablecoinClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/programmable")
@RequiredArgsConstructor
@Tag(name = "Programmable Money", description = "iMKRW 스테이블코인 발행 및 소각 API")
public class ProgrammableMoneyController {

    private final StablecoinClient stablecoinClient;

    @Operation(summary = "iMKRW 발행", description = """
            원화를 iMKRW 스테이블코인으로 1:1 발행합니다.
            
            발행된 iMKRW는 Kaia 블록체인 네트워크에 기록되며,
            미션 수락 시 스마트컨트랙트에 보증금으로 Lock-up됩니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발행 성공",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": {
                        "success": true,
                        "mintedAmount": 50000,
                        "txHash": "0xmint_abc123def456"
                    }
                }
                """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @PostMapping("/mint")
    public ApiResponse<StablecoinClient.TokenMintResult> mint(
            @AuthenticationPrincipal Long userId,
            @RequestBody MoneyRequest request) {
        return ApiResponse.success(stablecoinClient.mintToken(userId, request.getAmount()));
    }

    @Operation(summary = "iMKRW 소각", description = """
            iMKRW 스테이블코인을 원화로 소각 출금합니다.
            
            소각된 iMKRW는 Kaia 블록체인에서 영구 제거되며,
            동일 금액이 등록된 DAGAON 지갑으로 출금됩니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "소각 성공 (data: true 반환)",
            content = @Content(examples = @ExampleObject(value = """
                {
                    "success": true,
                    "data": true
                }
                """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @PostMapping("/burn")
    public ApiResponse<Boolean> burn(
            @AuthenticationPrincipal Long userId,
            @RequestBody MoneyRequest request) {
        return ApiResponse.success(stablecoinClient.burnToken(userId, request.getAmount()));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(description = "iMKRW 발행/소각 요청 본문")
    public static class MoneyRequest {
        @Schema(description = "금액 (원화 기준, 1원 = 1 iMKRW)", example = "50000")
        private Long amount;
    }
}
