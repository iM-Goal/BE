package com.im.agentic.api.goal.dto;

import com.im.agentic.domain.goal.entity.GoalSlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class GoalRequestDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(description = "저축 목표 등록 요청 DTO")
    public static class CreateGoalRequest {
        @NotBlank(message = "Item name cannot be blank")
        @Schema(description = "저축 구매 목표 아이템 이름", example = "나이키 에어맥스")
        private String itemName;

        @NotNull(message = "Target amount cannot be null")
        @Min(value = 1, message = "Target amount must be a positive number")
        @Schema(description = "목표 금액 (원화)", example = "128000")
        private Long targetAmount;

        @NotNull(message = "Slot type cannot be null")
        @Schema(description = "저축 슬롯 종류 (SHORT/MID/LONG)", example = "SHORT")
        private GoalSlotType slotType;

        @NotBlank(message = "Registration type cannot be blank")
        @Schema(description = "등록 방식 (NATURAL_LANGUAGE/MANUAL)", example = "NATURAL_LANGUAGE")
        private String registrationType; 

        @Schema(description = "상품 이미지 URL", example = "https://example.com/nike.jpg")
        private String itemImageUrl;

        @Schema(description = "자연어 등록 시 입력 텍스트", example = "나이키 에어맥스 사고싶어")
        private String rawInput;

        @Schema(description = "목표 달성 기간 (일 단위)", example = "30")
        private Integer durationDays;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(description = "저축 목표 수정 요청 DTO")
    public static class UpdateGoalRequest {
        @NotBlank(message = "Item name cannot be blank")
        @Schema(description = "수정할 목표 아이템 이름", example = "나이키 에어맥스 97")
        private String itemName;

        @NotNull(message = "Target amount cannot be null")
        @Min(value = 1, message = "Target amount must be a positive number")
        @Schema(description = "수정할 목표 금액 (원화)", example = "150000")
        private Long targetAmount;

        @Schema(description = "수정할 상품 이미지 URL", example = "https://example.com/nike97.jpg")
        private String itemImageUrl;
    }
}
