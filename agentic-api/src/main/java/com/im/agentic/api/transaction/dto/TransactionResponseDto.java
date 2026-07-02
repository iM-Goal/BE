package com.im.agentic.api.transaction.dto;

import com.im.agentic.domain.transaction.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class TransactionResponseDto {
    private Long id;
    private Long userId;
    private String merchant;
    private Long amount;
    private String category;
    private LocalDateTime transactedAt;

    public static TransactionResponseDto from(Transaction transaction) {
        if (transaction == null) return null;
        return TransactionResponseDto.builder()
                .id(transaction.getId())
                .userId(transaction.getUser().getId())
                .merchant(transaction.getMerchant())
                .amount(transaction.getAmount())
                .category(transaction.getCategory() != null ? transaction.getCategory().name() : null)
                .transactedAt(transaction.getTransactedAt())
                .build();
    }
}
