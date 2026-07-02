package com.im.agentic.api.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.dashboard.dto.DashboardResponseDto.*;
import com.im.agentic.api.goal.dto.GoalResponseDto.*;
import com.im.agentic.api.mission.dto.MissionResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.deposit.entity.Deposit;
import com.im.agentic.domain.deposit.repository.DepositRepository;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalSlotType;
import com.im.agentic.domain.goal.entity.GoalStatus;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.mission.entity.MissionStatus;
import com.im.agentic.domain.mission.repository.MissionRepository;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DashboardService {

    private final GoalRepository goalRepository;
    private final MissionRepository missionRepository;
    private final DepositRepository depositRepository;
    private final MyDataCacheRepository myDataCacheRepository;
    private final ObjectMapper objectMapper;

    public DashboardResponse getDashboard(Long userId) {
        // 1. 3슬롯 목표 진행률 구성
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, GoalStatus.ACTIVE);
        GoalResponse shortGoal = null;
        GoalResponse midGoal = null;
        GoalResponse longGoal = null;

        for (Goal goal : activeGoals) {
            if (goal.getSlotType() == GoalSlotType.SHORT) {
                shortGoal = GoalResponse.from(goal);
            } else if (goal.getSlotType() == GoalSlotType.MID) {
                midGoal = GoalResponse.from(goal);
            } else if (goal.getSlotType() == GoalSlotType.LONG) {
                longGoal = GoalResponse.from(goal);
            }
        }

        GoalListResponse goalsResponse = GoalListResponse.builder()
                .shortGoal(shortGoal)
                .midGoal(midGoal)
                .longGoal(longGoal)
                .build();

        // 2. 오늘 진행 중인 미션 목록 구성
        List<MissionResponseDto> todayMissions = missionRepository.findByUserIdAndStatus(userId, MissionStatus.IN_PROGRESS)
                .stream()
                .map(MissionResponseDto::from)
                .collect(Collectors.toList());

        // 3. 이번 주 달성률 계산 (최근 7일 미션 대상)
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);
        
        List<Mission> allMissions = missionRepository.findByUserId(userId);
        List<Mission> weeklyMissions = allMissions.stream()
                .filter(m -> m.getStartDate() != null && !m.getStartDate().isBefore(sevenDaysAgo) && !m.getStartDate().isAfter(today))
                .collect(Collectors.toList());

        long totalMissionsCount = weeklyMissions.size();
        long successfulMissionsCount = weeklyMissions.stream()
                .filter(m -> m.getStatus() == MissionStatus.SUCCESS)
                .count();
        String weeklyAchievementRate = successfulMissionsCount + "/" + totalMissionsCount;

        // 4. 보증금 총액 계산
        List<Deposit> deposits = depositRepository.findByUserId(userId);
        long totalSavedAmount = deposits.stream()
                .mapToLong(Deposit::getAmount)
                .sum();

        return DashboardResponse.builder()
                .goals(goalsResponse)
                .todayMissions(todayMissions)
                .weeklyAchievementRate(weeklyAchievementRate)
                .totalSavedAmount(totalSavedAmount)
                .build();
    }

    public SpendingGraphResponse getSpendingGraph(Long userId) {
        MyDataCache cache = myDataCacheRepository.findByUserId(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        try {
            Map<String, Long> categoryExpenses = objectMapper.readValue(
                    cache.getCategoryExpenses(), new TypeReference<Map<String, Long>>() {}
            );

            long totalAmount = categoryExpenses.values().stream().mapToLong(Long::longValue).sum();
            List<CategorySpending> categories = new ArrayList<>();

            for (Map.Entry<String, Long> entry : categoryExpenses.entrySet()) {
                double percentage = totalAmount > 0 ? (double) entry.getValue() / totalAmount * 100 : 0.0;
                categories.add(CategorySpending.builder()
                        .category(entry.getKey())
                        .amount(entry.getValue())
                        .percentage(Math.round(percentage * 10.0) / 10.0)
                        .build());
            }

            categories.sort((c1, c2) -> c2.getAmount().compareTo(c1.getAmount()));

            return SpendingGraphResponse.builder()
                    .categories(categories)
                    .totalAmount(totalAmount)
                    .build();

        } catch (Exception e) {
            log.error("[DashboardService] 카테고리 지출 파싱 실패: {}", e.getMessage());
            return SpendingGraphResponse.builder()
                    .categories(Collections.emptyList())
                    .totalAmount(0L)
                    .build();
        }
    }

    public WeeklyReportResponse getWeeklyReport(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        List<Mission> allMissions = missionRepository.findByUserId(userId);
        List<Mission> weeklyMissions = allMissions.stream()
                .filter(m -> m.getStartDate() != null && !m.getStartDate().isBefore(sevenDaysAgo) && !m.getStartDate().isAfter(today))
                .collect(Collectors.toList());

        long totalMissionsCount = weeklyMissions.size();
        long successfulMissionsCount = weeklyMissions.stream()
                .filter(m -> m.getStatus() == MissionStatus.SUCCESS)
                .count();

        // 절약 보증금 합산 (성공한 미션에 묶인 보증금)
        List<Deposit> userDeposits = depositRepository.findByUserId(userId);
        long totalSavedAmount = userDeposits.stream()
                .filter(d -> d.getLockedAt() != null && !d.getLockedAt().toLocalDate().isBefore(sevenDaysAgo) && !d.getLockedAt().toLocalDate().isAfter(today))
                .filter(d -> d.getStatus() == com.im.agentic.domain.deposit.entity.DepositStatus.RELEASED)
                .mapToLong(Deposit::getAmount)
                .sum();

        // 전체 누적 보증금 합산
        long totalSavingAmount = userDeposits.stream()
                .mapToLong(Deposit::getAmount)
                .sum();

        long daysLeftChange = 0;
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, GoalStatus.ACTIVE);
        if (!activeGoals.isEmpty()) {
            Goal priorityGoal = activeGoals.get(0);
            if (priorityGoal.getEstimatedCompletionDate() != null) {
                long daysLeftNow = ChronoUnit.DAYS.between(today, priorityGoal.getEstimatedCompletionDate());
                long daysLeftPast = ChronoUnit.DAYS.between(sevenDaysAgo, priorityGoal.getEstimatedCompletionDate());
                daysLeftChange = Math.max(0, daysLeftPast) - Math.max(0, daysLeftNow);
            }
        }

        String period = sevenDaysAgo.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " ~ " + today.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

        return WeeklyReportResponse.builder()
                .missionAchievementRate(successfulMissionsCount + "/" + totalMissionsCount)
                .totalSavedAmount(totalSavedAmount)
                .totalSavingAmount(totalSavingAmount)
                .daysLeftChange(daysLeftChange)
                .period(period)
                .build();
    }
}
