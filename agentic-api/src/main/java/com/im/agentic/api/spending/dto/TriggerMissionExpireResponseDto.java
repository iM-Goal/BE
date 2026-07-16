package com.im.agentic.api.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "미션 만기 정산 트리거 응답 DTO")
public class TriggerMissionExpireResponseDto {

    @Schema(description = "API 처리 성공 여부")
    private boolean success;

    @Schema(description = "처리된 미션 ID", example = "5")
    private Long missionId;

    @Schema(description = "AI 판정 결과: SUCCEED 또는 FAILED", example = "SUCCEED")
    private String judgeResult;

    @Schema(description = "보증금 반환 여부", example = "true")
    private boolean depositReturned;

    @Schema(description = "결과 설명 메시지", example = "미션 성공! 보증금이 반환되었습니다.")
    private String message;
}
