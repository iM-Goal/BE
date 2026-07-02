package com.im.agentic.api.transaction.controller;

import com.im.agentic.api.transaction.dto.TransactionResponseDto;
import com.im.agentic.api.transaction.service.TransactionService;
import com.im.agentic.common.response.ApiResponse;
import com.im.agentic.domain.transaction.entity.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction", description = "소비 거래 내역 등록 및 조회 API")
public class TransactionController {

    private final TransactionService transactionService;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CardPaymentRequest {
        private String merchant;
        private Long amount;
        private String category;
    }

    @Operation(summary = "카드 결제 승인 (소비 등록)", description = "모의 카드가 사용되었을 때 소비 내역을 거래 장부에 등록합니다.")
    @PostMapping("/pay")
    public ApiResponse<TransactionResponseDto> pay(
            @AuthenticationPrincipal Long userId,
            @RequestBody CardPaymentRequest request) {
        Transaction tx = transactionService.saveTransaction(
                userId, request.getMerchant(), request.getAmount(), request.getCategory()
        );
        return ApiResponse.success(TransactionResponseDto.from(tx));
    }

    @Operation(summary = "거래 내역 조회", description = "사용자의 전체 소비 이체 내역을 최신순으로 반환합니다.")
    @GetMapping("/history")
    public ApiResponse<List<TransactionResponseDto>> getHistory(
            @AuthenticationPrincipal Long userId) {
        List<TransactionResponseDto> history = transactionService.getHistory(userId)
                .stream()
                .map(TransactionResponseDto::from)
                .collect(Collectors.toList());
        return ApiResponse.success(history);
    }
}
