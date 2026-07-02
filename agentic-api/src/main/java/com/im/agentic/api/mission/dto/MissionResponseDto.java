package com.im.agentic.api.mission.dto;

import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.mission.entity.MissionSource;
import com.im.agentic.domain.mission.entity.MissionStatus;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "미션 정보 응답 DTO")
public class MissionResponseDto {
    @Schema(description = "미션 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 ID", example = "42")
    private Long userId;

    @Schema(description = "연관 목표 ID (자동 미션의 경우 null)", example = "12")
    private Long goalId;

    @Schema(description = "미션 생성 소스 (DIRECT/AUTO)", example = "DIRECT")
    private MissionSource missionSource;

    @Schema(description = "미션 상태 (PROPOSED/IN_PROGRESS/SUCCESS/FAIL)", example = "PROPOSED")
    private MissionStatus status;

    @Schema(description = "제약 소비 카테고리 (FOOD/CAFE/etc.)", example = "CAFE")
    private SpendingCategory category;

    @Schema(description = "미션 설명 행동 지침", example = "카페 지출을 3,000원 이하로 줄여보세요")
    private String description;

    @Schema(description = "소비 제한 금액 (한도)", example = "3000")
    private Long limitAmount;

    @Schema(description = "미션 보증금", example = "15000")
    private Long depositAmount;

    @Schema(description = "미션 진행 기간 (일 단위)", example = "3")
    private Integer durationDays;

    @Schema(description = "미션 시작일", example = "2026-06-30")
    private LocalDate startDate;

    @Schema(description = "미션 종료일", example = "2026-07-03")
    private LocalDate endDate;

    @Schema(description = "예산 대비 과소비 초과율 (퍼센트)", example = "34.5")
    private Double overageRate;

    @Schema(description = "미션 제안 사유", example = "오늘 가용 잔액을 초과했습니다.")
    private String proposalReason;

    public static MissionResponseDto from(Mission mission) {
        if (mission == null) return null;
        return MissionResponseDto.builder()
                .id(mission.getId())
                .userId(mission.getUser().getId())
                .goalId(mission.getGoal() != null ? mission.getGoal().getId() : null)
                .missionSource(mission.getMissionSource())
                .status(mission.getStatus())
                .category(mission.getCategory())
                .description(mission.getDescription())
                .limitAmount(mission.getLimitAmount())
                .depositAmount(mission.getDepositAmount())
                .durationDays(mission.getDurationDays())
                .startDate(mission.getStartDate())
                .endDate(mission.getEndDate())
                .overageRate(mission.getOverageRate())
                .proposalReason(mission.getProposalReason())
                .build();
    }
}
