package com.im.agentic.api.deposit.dto;

import com.im.agentic.domain.deposit.entity.Deposit;
import com.im.agentic.domain.deposit.entity.DepositStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "보증금 상세 정보 응답 DTO")
public class DepositResponseDto {
    @Schema(description = "보증금 ID", example = "1")
    private Long id;

    @Schema(description = "연관 미션 ID", example = "3")
    private Long missionId;

    @Schema(description = "사용자 ID", example = "42")
    private Long userId;

    @Schema(description = "보증금 원화 환산액 (KRW)", example = "15000")
    private Long amount;

    @Schema(description = "발행된 iMKRW 발행량", example = "15000")
    private Long imkrwAmount;

    @Schema(description = "보증금 상태 (LOCKED/RELEASED/WITHDRAWN)", example = "LOCKED")
    private DepositStatus status;

    @Schema(description = "Kaia 스마트컨트랙트 잠금 트랜잭션 해시", example = "0xlock123abc")
    private String lockTxHash;

    @Schema(description = "Kaia 스마트컨트랙트 잠금 해제 트랜잭션 해시", example = "0xrelease456def")
    private String releaseTxHash;

    @Schema(description = "보증금 잠금 일시", example = "2026-06-30T10:00:00")
    private LocalDateTime lockedAt;

    @Schema(description = "보증금 잠금 해제 일시", example = "2026-07-03T23:00:00")
    private LocalDateTime releasedAt;

    public static DepositResponseDto from(Deposit deposit) {
        if (deposit == null) return null;
        return DepositResponseDto.builder()
                .id(deposit.getId())
                .missionId(deposit.getMission().getId())
                .userId(deposit.getUser().getId())
                .amount(deposit.getAmount())
                .imkrwAmount(deposit.getImkrwAmount())
                .status(deposit.getStatus())
                .lockTxHash(deposit.getLockTxHash())
                .releaseTxHash(deposit.getReleaseTxHash())
                .lockedAt(deposit.getLockedAt())
                .releasedAt(deposit.getReleasedAt())
                .build();
    }
}
