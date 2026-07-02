package com.im.agentic.api.programmable.controller;

import com.im.agentic.common.response.ApiResponse;
import com.im.agentic.infra.blockchain.StablecoinClient;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "iMKRW 발행", description = "원화를 iMKRW 스테이블코인으로 1:1 발행합니다.")
    @PostMapping("/mint")
    public ApiResponse<StablecoinClient.TokenMintResult> mint(
            @AuthenticationPrincipal Long userId,
            @RequestBody MoneyRequest request) {
        return ApiResponse.success(stablecoinClient.mintToken(userId, request.getAmount()));
    }

    @Operation(summary = "iMKRW 소각", description = "iMKRW 스테이블코인을 원화로 소각 출금합니다.")
    @PostMapping("/burn")
    public ApiResponse<Boolean> burn(
            @AuthenticationPrincipal Long userId,
            @RequestBody MoneyRequest request) {
        return ApiResponse.success(stablecoinClient.burnToken(userId, request.getAmount()));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MoneyRequest {
        private Long amount;
    }
}
