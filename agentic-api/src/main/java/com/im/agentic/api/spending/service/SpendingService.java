package com.im.agentic.api.spending.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.spending.dto.SpendingTodayResponseDto;
import com.im.agentic.api.spending.dto.SpendingWeeklyResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
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
}
