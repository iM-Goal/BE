package com.im.agentic.api.planning.controller;

import com.im.agentic.api.planning.dto.PlanReportRequestDto;
import com.im.agentic.api.planning.dto.PlanReportResponseDto;
import com.im.agentic.api.planning.service.PlanningReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Planning", description = "맞춤형 재무 계획서 & 컴플라이언스 레포트 API")
public class PlanningReportController {

    private final PlanningReportService planningReportService;

    @PostMapping("/report")
    @Operation(summary = "2트랙 맞춤형 계획서 레포트 생성 (소비형 vs 자산형성형)",
               description = "일일 가용예산(dailyAvailable)과 마이데이터 지출 패턴을 분석하여 계획서를 생성하고, 내부통제 AI가 금소법 6대 원칙 적합성 검증 및 담당자 연결 여부를 판정합니다.")
    public ResponseEntity<PlanReportResponseDto> generateReport(@RequestBody PlanReportRequestDto request) {
        log.info("[PlanningController] 레포트 생성 API 호출: userId={}", request.userId());
        PlanReportResponseDto response = planningReportService.generatePlanReport(request);
        return ResponseEntity.ok(response);
    }
}
