package com.im.agentic.api.spending.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.goal.service.GoalService;
import com.im.agentic.api.mission.service.MissionService;
import com.im.agentic.api.programmable.service.ProgrammableMoneyService;
import com.im.agentic.api.spending.dto.TriggerBuyOutResponseDto;
import com.im.agentic.api.spending.dto.TriggerMissionExpireResponseDto;
import com.im.agentic.api.spending.dto.TriggerSalaryRequestDto;
import com.im.agentic.api.spending.dto.TriggerSalaryResponseDto;
import com.im.agentic.api.spending.dto.TriggerSpendingRequestDto;
import com.im.agentic.api.spending.dto.TriggerSpendingResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalStatus;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.mission.entity.MissionStatus;
import com.im.agentic.domain.mission.repository.MissionRepository;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mock", description = "시연 및 데모용 Mock API")
public class MockSpendingController {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final MissionRepository missionRepository;
    private final MyDataCacheRepository myDataCacheRepository;
    private final AgentClient agentClient;
    private final GoalService goalService;
    private final MissionService missionService;
    private final ProgrammableMoneyService programmableMoneyService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────
    // 시나리오 0: 가짜 결제 발생 + AI 과소비 감지
    // ─────────────────────────────────────────────────────────

    @PostMapping("/trigger-spending")
    @Operation(summary = "가짜 결제 발생 및 실시간 알림 반환",
               description = "결제를 트리거하고 AI 과소비 감지 결과를 즉시 반환합니다.")
    public ResponseEntity<TriggerSpendingResponseDto> triggerMockSpending(@RequestBody TriggerSpendingRequestDto request) {
        log.info("[Mock API] 가짜 결제 트리거 요청: {}", request);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        MyDataCache cache = myDataCacheRepository.findByUserId(request.userId())
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        SpendingCategory category;
        try {
            category = SpendingCategory.valueOf(request.category().toUpperCase());
        } catch (IllegalArgumentException e) {
            category = SpendingCategory.SHOPPING;
        }

        transactionRepository.save(Transaction.builder()
                .user(user).merchant(request.merchantName())
                .amount(request.amount()).category(category)
                .transactedAt(LocalDateTime.now()).build());

        LocalDateTime startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime endOfWeek = startOfWeek.plusDays(7).minusNanos(1);
        List<Transaction> thisWeekTxs = transactionRepository
                .findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(request.userId(), startOfWeek, endOfWeek);

        Map<String, Long> weeklyByCategory = new HashMap<>();
        for (Transaction tx : thisWeekTxs) {
            if (tx.getCategory() != null) {
                String korName = mapToKorean(tx.getCategory());
                weeklyByCategory.put(korName, weeklyByCategory.getOrDefault(korName, 0L) + tx.getAmount());
            }
        }

        Map<String, Long> myWeeklyAvg = new HashMap<>();
        try {
            myWeeklyAvg = objectMapper.readValue(cache.getWeeklyAvgExpenses(),
                    new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.warn("[Mock API] weeklyAvgExpenses 파싱 실패: {}", e.getMessage());
        }

        AgentClient.AutoOverageResponse overageResponse = agentClient.detectAutoOverage(
                request.userId(), weeklyByCategory, myWeeklyAvg, user.getIncomeQuintile());

        return ResponseEntity.ok(new TriggerSpendingResponseDto(
                true,
                overageResponse.isHasOverage(),
                overageResponse.isHasOverage() ? overageResponse.getProposalReason() : null));
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 1: 월급 입금 → 목표 통장 비례 자동이체
    // ─────────────────────────────────────────────────────────

    @PostMapping("/trigger-salary")
    @Operation(summary = "월급 입금 시뮬레이션 → 목표 자동이체",
               description = "savingRatio(%) 만큼 저축하고 targetAmount 비율로 각 ACTIVE Goal에 비례 분배합니다.")
    public ResponseEntity<TriggerSalaryResponseDto> triggerSalary(@RequestBody TriggerSalaryRequestDto request) {
        log.info("[Mock API] 월급 입금 트리거: userId={}, salary={}, ratio={}%",
                request.userId(), request.salaryAmount(), request.savingRatio());

        userRepository.findById(request.userId())
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // 1. 총 저축액 계산
        long totalSaving = request.salaryAmount() * request.savingRatio() / 100;

        // 2. ACTIVE Goal 목록 조회
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(request.userId(), GoalStatus.ACTIVE);
        if (activeGoals.isEmpty()) {
            return ResponseEntity.ok(TriggerSalaryResponseDto.builder()
                    .success(false).totalSavingAmount(totalSaving).allocations(List.of()).build());
        }

        // 3. targetAmount 합계로 비율 계산
        long totalTarget = activeGoals.stream().mapToLong(Goal::getTargetAmount).sum();
        long remainingSaving = totalSaving;

        // targetAmount 가장 큰 Goal을 나머지 오차 합산 대상으로 지정
        Goal largestGoal = activeGoals.stream()
                .max((a, b) -> Long.compare(a.getTargetAmount(), b.getTargetAmount()))
                .orElse(activeGoals.get(0));

        List<TriggerSalaryResponseDto.GoalAllocation> allocations = new ArrayList<>();

        for (Goal goal : activeGoals) {
            long transfer;
            if (goal.getId().equals(largestGoal.getId())) {
                // 나머지 오차를 가장 큰 Goal에 합산
                transfer = remainingSaving;
            } else {
                transfer = totalSaving * goal.getTargetAmount() / totalTarget;
                remainingSaving -= transfer;
            }

            // 4. Goal 잔액 업데이트
            goal.updateProgress(transfer);
            goalRepository.save(goal);

            // 5. iMKRW 발행 + Kaia 잠금 (블록체인 연동)
            try {
                programmableMoneyService.transferWithToken(request.userId(), goal.getId(), transfer);
            } catch (Exception e) {
                log.warn("[Mock API] 블록체인 처리 실패 (저축은 반영됨): goalId={}, error={}", goal.getId(), e.getMessage());
            }

            allocations.add(TriggerSalaryResponseDto.GoalAllocation.builder()
                    .goalId(goal.getId())
                    .goalName(goal.getItemName())
                    .targetAmount(goal.getTargetAmount())
                    .transferredAmount(transfer)
                    .currentAmount(goal.getCurrentAmount())
                    .achievementRate(goal.getAchievementRate())
                    .build());
        }

        return ResponseEntity.ok(TriggerSalaryResponseDto.builder()
                .success(true).totalSavingAmount(totalSaving).allocations(allocations).build());
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 2: 마지막 저축 → Buy-out 트리거
    // ─────────────────────────────────────────────────────────

    @PostMapping("/trigger-buyout/{goalId}")
    @Operation(summary = "마지막 저축 자동 충전 + Buy-out 실행",
               description = "목표 부족분을 자동 충전한 뒤 iMKRW 소각 → Kaia 잠금 해제 → DAGAON 출금까지 한 번에 처리합니다.")
    public ResponseEntity<TriggerBuyOutResponseDto> triggerBuyOut(
            @PathVariable Long goalId,
            @RequestParam Long userId) {
        log.info("[Mock API] Buy-out 트리거: userId={}, goalId={}", userId, goalId);

        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        // 1. 부족분 계산 후 마지막 저축 충전
        long lastSaving = Math.max(0, goal.getTargetAmount() - goal.getCurrentAmount());
        if (lastSaving > 0) {
            goal.updateProgress(lastSaving);
            goalRepository.save(goal);
            log.info("[Mock API] 마지막 저축 충전: {}원", lastSaving);
        }

        // 2. 기존 GoalService.buyOut 재활용 (iMKRW 소각 + Kaia 해제 + DAGAON 출금)
        var buyOutResult = goalService.buyOut(userId, goalId);

        // 3. 조사 처리 적용 메시지 생성
        String particle = getObjectParticle(goal.getItemName());
        String message = String.format(
                "목표 달성! %,d iMKRW 소각 완료. DAGAON 지갑으로 %,d원 출금. %s%s 구매하세요!",
                goal.getCurrentAmount(), goal.getCurrentAmount(), goal.getItemName(), particle);

        return ResponseEntity.ok(TriggerBuyOutResponseDto.builder()
                .success(true)
                .goalId(goalId)
                .itemName(goal.getItemName())
                .lastSavingAmount(lastSaving)
                .totalAmount(goal.getCurrentAmount())
                .message(message)
                .build());
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 3: 미션 만기 정산 트리거
    // ─────────────────────────────────────────────────────────

    @PostMapping("/trigger-mission-expire/{missionId}")
    @Operation(summary = "미션 만기 정산 시뮬레이션",
               description = "DB의 end_date를 수정하지 않고 즉시 AI 판정을 실행합니다. Mission.status가 SUCCEED/FAILED로 변경됩니다.")
    public ResponseEntity<TriggerMissionExpireResponseDto> triggerMissionExpire(
            @PathVariable Long missionId) {
        log.info("[Mock API] 미션 만기 트리거: missionId={}", missionId);

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new AgenticException(ErrorCode.MISSION_NOT_FOUND));

        if (mission.getStatus() != MissionStatus.IN_PROGRESS) {
            return ResponseEntity.ok(TriggerMissionExpireResponseDto.builder()
                    .success(false).missionId(missionId)
                    .judgeResult(mission.getStatus().name())
                    .depositReturned(false)
                    .message("이미 처리된 미션입니다. 현재 상태: " + mission.getStatus().name())
                    .build());
        }

        // 기존 MissionService.judgeMission 재활용 (DB end_date 수정 없음)
        missionService.judgeMission(missionId);

        // 판정 후 상태 재조회
        Mission judged = missionRepository.findById(missionId)
                .orElseThrow(() -> new AgenticException(ErrorCode.MISSION_NOT_FOUND));

        boolean isSuccess = judged.getStatus() == MissionStatus.SUCCESS;
        String message = isSuccess
                ? "미션 성공! 보증금이 반환되었습니다."
                : "미션 실패. 보증금은 잠금 상태로 유지됩니다.";

        return ResponseEntity.ok(TriggerMissionExpireResponseDto.builder()
                .success(true)
                .missionId(missionId)
                .judgeResult(judged.getStatus().name())
                .depositReturned(isSuccess)
                .message(message)
                .build());
    }

    // ─────────────────────────────────────────────────────────
    // 유틸 메서드
    // ─────────────────────────────────────────────────────────

    /**
     * 한국어 조사 처리: 마지막 글자의 종성(받침) 유무에 따라 "을"/"를" 반환
     * 종성 유무 판별: (유니코드 코드포인트 - '가') % 28 > 0 이면 받침 있음 → "을"
     */
    private String getObjectParticle(String noun) {
        if (noun == null || noun.isBlank()) return "을";
        char last = noun.charAt(noun.length() - 1);
        if (last < '가' || last > '힣') return "을"; // 한글 범위 밖이면 기본값
        return (last - '가') % 28 > 0 ? "을" : "를";
    }

    private String mapToKorean(SpendingCategory category) {
        if (category == null) return "기타";
        return switch (category) {
            case CAFE -> "카페";
            case DELIVERY -> "배달음식";
            case CONVENIENCE -> "편의점";
            case FOOD -> "식비";
            case TRANSPORT -> "교통";
            case SHOPPING -> "쇼핑";
        };
    }
}

