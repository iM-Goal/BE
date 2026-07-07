package com.im.agentic.api.spending.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.spending.dto.SpendingPersonalAnalysisDto;
import com.im.agentic.api.spending.dto.SpendingTodayResponseDto;
import com.im.agentic.api.spending.dto.SpendingWeeklyResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SpendingService {

    private final TransactionRepository transactionRepository;
    private final MyDataCacheRepository myDataCacheRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // 오늘 소비 현황
    public SpendingTodayResponseDto getToday(Long userId) {
        MyDataCache cache = myDataCacheRepository.findByUserId(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        long dailyBudget = cache.getDailyAvailableAmount();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todaySpend = transactionRepository.sumTodaySpend(userId, startOfDay);

        long remainingBudget = dailyBudget - todaySpend;
        double usageRate = dailyBudget > 0 ? ((double) todaySpend / dailyBudget * 100) : 0.0;

        return SpendingTodayResponseDto.builder()
                .dailyBudget(dailyBudget)
                .todaySpend(todaySpend)
                .remainingBudget(remainingBudget)
                .usageRate(usageRate)
                .build();
    }

    // 주간 리포트
    public SpendingWeeklyResponseDto getWeekly(Long userId) {
        MyDataCache cache = myDataCacheRepository.findByUserId(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // 이번 주 월요일 00:00 ~ 일요일 23:59
        LocalDateTime startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime endOfWeek = startOfWeek.plusDays(7).minusNanos(1);

        List<Transaction> thisWeekTxs = transactionRepository
                .findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(userId, startOfWeek, endOfWeek);

        // 카테고리별 소비 집계
        Map<SpendingCategory, Long> currentWeeklyExpenses = new HashMap<>();
        long totalSpend = 0L;
        for (Transaction tx : thisWeekTxs) {
            if (tx.getCategory() != null) {
                currentWeeklyExpenses.put(tx.getCategory(), currentWeeklyExpenses.getOrDefault(tx.getCategory(), 0L) + tx.getAmount());
                totalSpend += tx.getAmount();
            }
        }

        // 평균 소비량 파싱
        Map<String, Long> weeklyAvgExpenses = new HashMap<>();
        try {
            weeklyAvgExpenses = objectMapper.readValue(cache.getWeeklyAvgExpenses(),
                    new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.error("[SpendingService] weeklyAvgExpenses 파싱 실패: {}", e.getMessage());
        }

        List<SpendingWeeklyResponseDto.CategoryWeeklyReport> categoryReports = new ArrayList<>();
        for (SpendingCategory cat : SpendingCategory.values()) {
            long amount = currentWeeklyExpenses.getOrDefault(cat, 0L);
            String korName = mapToKorean(cat);
            long avgAmount = weeklyAvgExpenses.getOrDefault(korName, 0L);

            double vsLastWeek = 0.0;
            if (avgAmount > 0) {
                vsLastWeek = ((double) (amount - avgAmount) / avgAmount * 100);
            }

            categoryReports.add(SpendingWeeklyResponseDto.CategoryWeeklyReport.builder()
                    .category(korName)
                    .amount(amount)
                    .vsLastWeek(vsLastWeek)
                    .build());
        }

        return SpendingWeeklyResponseDto.builder()
                .totalSpend(totalSpend)
                .categories(categoryReports)
                .build();
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

    // ── 개인화 소비 분석 (본인 재정 기반) ──
    public SpendingPersonalAnalysisDto getPersonalAnalysis(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        MyDataCache cache = myDataCacheRepository.findByUserId(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // 1. 재정 기반 정보
        long monthlyIncome   = user.getMonthlyIncome()   != null ? user.getMonthlyIncome()   : 0L;
        long fixedExpense    = user.getFixedExpense()    != null ? user.getFixedExpense()    : 0L;
        long disposableIncome = user.getDisposableIncome() != null ? user.getDisposableIncome() : (monthlyIncome - fixedExpense);
        long dailyBudget     = user.getDailyAvailable()  != null ? user.getDailyAvailable()  : cache.getDailyAvailableAmount();

        // 2. 오늘 소비
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todaySpend = transactionRepository.sumTodaySpend(userId, startOfDay);
        long todayRemaining = dailyBudget - todaySpend;
        double todayUsageRate = dailyBudget > 0 ? ((double) todaySpend / dailyBudget * 100) : 0.0;

        // 3. 이번 주 카테고리별 집계
        LocalDateTime startOfWeek = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime endOfWeek = startOfWeek.plusDays(7).minusNanos(1);
        List<Transaction> thisWeekTxs = transactionRepository
                .findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(userId, startOfWeek, endOfWeek);

        Map<SpendingCategory, Long> weeklyByCategory = new HashMap<>();
        long weeklyTotalSpend = 0L;
        for (Transaction tx : thisWeekTxs) {
            if (tx.getCategory() != null) {
                weeklyByCategory.put(tx.getCategory(),
                        weeklyByCategory.getOrDefault(tx.getCategory(), 0L) + tx.getAmount());
                weeklyTotalSpend += tx.getAmount();
            }
        }

        // 4. 본인 4주 평균 파싱 (MyDataCache.weeklyAvgExpenses)
        Map<String, Long> myWeeklyAvg = new HashMap<>();
        try {
            myWeeklyAvg = objectMapper.readValue(cache.getWeeklyAvgExpenses(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.warn("[SpendingService] weeklyAvgExpenses 파싱 실패, 기본값 사용: {}", e.getMessage());
        }

        // 5. 카테고리별 개인화 리포트 생성
        List<SpendingPersonalAnalysisDto.CategoryPersonalReport> categoryReports = new ArrayList<>();
        List<String> overSpendingCategories = new ArrayList<>();

        for (SpendingCategory cat : SpendingCategory.values()) {
            long thisWeek = weeklyByCategory.getOrDefault(cat, 0L);
            String korName = mapToKorean(cat);
            long avgAmount = myWeeklyAvg.getOrDefault(korName, 0L);

            double vsMyAvgRate = 0.0;
            if (avgAmount > 0) {
                vsMyAvgRate = ((double)(thisWeek - avgAmount) / avgAmount * 100);
            }
            double pctOfDailyBudget = dailyBudget > 0 ? ((double) thisWeek / dailyBudget * 100) : 0.0;
            boolean isOverSpending = vsMyAvgRate >= 20.0; // 본인 평균 대비 20% 이상 초과 시 과소비

            if (isOverSpending) {
                overSpendingCategories.add(korName);
            }

            categoryReports.add(SpendingPersonalAnalysisDto.CategoryPersonalReport.builder()
                    .category(korName)
                    .thisWeekAmount(thisWeek)
                    .myWeeklyAvg(avgAmount)
                    .vsMyAvgRate(vsMyAvgRate)
                    .pctOfDailyBudget(pctOfDailyBudget)
                    .isOverSpending(isOverSpending)
                    .build());
        }

        // 6. 월간 예산 초과 예상 (이번 달 지난 일수 기준 추세 연장)
        long daysPassed = Math.max(1, java.time.LocalDate.now().getDayOfMonth());
        long projectedMonthlySpend = weeklyTotalSpend * 30L / Math.max(1, (daysPassed > 7 ? daysPassed : 7));
        boolean projectedOverBudget = projectedMonthlySpend > disposableIncome;

        // 7. 주간 예산 사용률 (dailyBudget × 7)
        long weeklyBudget = dailyBudget * 7;
        double weeklyBudgetUsageRate = weeklyBudget > 0 ? ((double) weeklyTotalSpend / weeklyBudget * 100) : 0.0;

        return SpendingPersonalAnalysisDto.builder()
                .monthlyIncome(monthlyIncome)
                .fixedExpense(fixedExpense)
                .disposableIncome(disposableIncome)
                .dailyBudget(dailyBudget)
                .todaySpend(todaySpend)
                .todayRemaining(todayRemaining)
                .todayUsageRate(todayUsageRate)
                .weeklyTotalSpend(weeklyTotalSpend)
                .weeklyBudgetUsageRate(weeklyBudgetUsageRate)
                .categories(categoryReports)
                .overSpendingCategories(overSpendingCategories)
                .projectedOverBudget(projectedOverBudget)
                .projectedMonthlySpend(projectedMonthlySpend)
                .build();
    }
}
