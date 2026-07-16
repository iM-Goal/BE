package com.im.agentic.api.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "마지막 저축 + Buy-out 트리거 응답 DTO")
public class TriggerBuyOutResponseDto {

    @Schema(description = "처리 성공 여부")
    private boolean success;

    @Schema(description = "처리된 목표 ID", example = "1")
    private Long goalId;

    @Schema(description = "목표 상품명", example = "에어팟 프로")
    private String itemName;

    @Schema(description = "마지막으로 자동 충전된 금액 (targetAmount - currentAmount)", example = "130000")
    private Long lastSavingAmount;

    @Schema(description = "최종 총 저축액", example = "320000")
    private Long totalAmount;

    @Schema(description = "시스템 고정 템플릿 메시지 (조사 자동 처리 적용)", example = "목표 달성! 320,000 iMKRW 소각 완료. DAGAON 지갑으로 320,000원 출금. 에어팟 프로를 구매하세요!")
    private String message;
}
