package com.im.agentic.api.deposit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "보증금 출금 미리보기 응답 DTO")
public class WithdrawPreviewResponseDto {
    @Schema(description = "보증금 ID", example = "1")
    private Long depositId;

    @Schema(description = "출금으로 인한 저축 목표 달성 지연 일수", example = "3")
    private Long goalDelayDays;

    @Schema(description = "출금 승인 확인용 4자리 코드", example = "8472")
    private String confirmCode;
}
