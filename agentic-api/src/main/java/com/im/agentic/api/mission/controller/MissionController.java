package com.im.agentic.api.mission.controller;

import com.im.agentic.api.deposit.dto.DepositResponseDto;
import com.im.agentic.api.mission.dto.MissionResponseDto;
import com.im.agentic.api.mission.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.im.agentic.common.response.ApiResponse.success;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
@Tag(name = "Mission", description = "직접 미션 / 자동 미션 관리 API")
public class MissionController {

    private final MissionService missionService;

    @Operation(summary = "오늘 진행중인 미션 조회", description = "IN_PROGRESS 상태의 미션 목록을 반환합니다.")
    @GetMapping("/today")
    public com.im.agentic.common.response.ApiResponse<List<MissionResponseDto>> getTodayMissions(
            @AuthenticationPrincipal Long userId) {
        return success(missionService.getTodayMissions(userId));
    }

    @Operation(summary = "제안된 미션 조회", description = "AI가 제안한 PROPOSED 상태의 미션 목록을 반환합니다. 직접 미션과 자동 미션이 모두 포함됩니다.")
    @GetMapping("/proposals")
    public com.im.agentic.common.response.ApiResponse<List<MissionResponseDto>> getProposedMissions(
            @AuthenticationPrincipal Long userId) {
        return success(missionService.getProposedMissions(userId));
    }

    @Operation(summary = "미션 상세 조회")
    @GetMapping("/{id}")
    public com.im.agentic.common.response.ApiResponse<MissionResponseDto> getMission(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return success(missionService.getMission(userId, id));
    }

    @Operation(summary = "미션 수락", description = "제안된 미션을 수락하여 보증금을 iMKRW로 발행하고 Kaia 스마트 컨트랙트에 잠급니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수락 성공, 보증금 잠금 완료"),
        @ApiResponse(responseCode = "404", description = "미션을 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "이미 수락되었거나 제안 상태가 아님")
    })
    @PostMapping("/{id}/accept")
    public com.im.agentic.common.response.ApiResponse<DepositResponseDto> acceptMission(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return success(missionService.acceptMission(userId, id));
    }
}
