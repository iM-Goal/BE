package com.im.agentic.api.mydata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.mydata.dto.CardApprovalRequest;
import com.im.agentic.api.mydata.dto.CategorySummaryResponse;
import com.im.agentic.api.mydata.dto.MyDataCacheResponseDto;
import com.im.agentic.api.mydata.dto.ThresholdResponse;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.mydata.entity.MerchantCategoryMapping;
import com.im.agentic.domain.mydata.entity.MyDataCardApproval;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.CategorySum;
import com.im.agentic.domain.mydata.repository.MerchantCategoryMappingRepository;
import com.im.agentic.domain.mydata.repository.MyDataCardApprovalRepository;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MyDataService {

    private final MyDataCacheRepository myDataCacheRepository;
    private final UserRepository userRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final ObjectMapper objectMapper;
    private final MyDataCardApprovalRepository cardApprovalRepository;
    private final MerchantCategoryMappingRepository merchantMappingRepository;


    @Transactional
    public MyDataCacheResponseDto analyzeAndCache(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // 1. Fetch analyzed spending map from SpendingAnalysisAgent via Orchestrator
        Map<String, Long> categoryExpensesMap = agentOrchestrator.analyzeSpending(userId);

        String categoryExpensesJson;
        try {
            categoryExpensesJson = objectMapper.writeValueAsString(categoryExpensesMap);
        } catch (Exception e) {
            log.error("[MyDataService] JSON conversion error: {}", e.getMessage());
            categoryExpensesJson = "{}";
        }

        // 2. Estimate monthly income and calculate daily available amount
        long totalExpenses = categoryExpensesMap.values().stream().mapToLong(Long::longValue).sum();
        long estimatedMonthlyIncome = Math.max(3000000L, totalExpenses + 600000L);
        long dailyAvailableAmount = (estimatedMonthlyIncome - totalExpenses) / 30;


        // 4. Update or save MyDataCache
        MyDataCache cache = myDataCacheRepository.findByUserId(userId).orElse(null);
        if (cache != null) {
            String expensesJson = categoryExpensesMap.isEmpty() ? "{}" : serializeJsonMap(categoryExpensesMap);
            cache.update(expensesJson, cache.getWeeklyAvgExpenses(), dailyAvailableAmount);
        } else {
            cache = MyDataCache.builder()
                    .user(user)
                    .categoryExpenses(serializeJsonMap(categoryExpensesMap))
                    .weeklyAvgExpenses("{}")
                    .dailyAvailableAmount(dailyAvailableAmount)
                    .lastUpdatedAt(LocalDateTime.now())
                    .build();
        }

        MyDataCache savedCache = myDataCacheRepository.save(cache);
        return MyDataCacheResponseDto.from(savedCache);
    }

    public MyDataCacheResponseDto getCache(Long userId) {
        return myDataCacheRepository.findByUserId(userId)
                .map(MyDataCacheResponseDto::from)
                .orElseGet(() -> analyzeAndCache(userId));
    }

    private String serializeJsonMap(Map<String, Long> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static final Map<Integer, Double> OVERAGE_THRESHOLD = Map.of(
        1, 0.10,   // [실제값] 소비성향 133.6% -> 이미 적자
        2, 0.20,   // [추정값]
        3, 0.25,   // [추정값] (기존 고정값과 동일)
        4, 0.33,   // [추정값]
        5, 0.45    // [실제값] 소비성향 55.0%
    );

    @Transactional
    public void importCardApprovals(Long userId, List<CardApprovalRequest> requests) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        List<MyDataCardApproval> entities = requests.stream()
            .map(req -> {
                String category = classifyMerchant(req.merchantName());
                return MyDataCardApproval.builder()
                    .userId(user.getId())
                    .approvedNum(req.approvedNum())
                    .approvedDtime(parseDateTime(req.approvedDtime()))
                    .status(req.status())
                    .merchantName(req.merchantName())
                    .approvedAmt(req.approvedAmt())
                    .category(category)
                    .build();
            })
            .collect(Collectors.toList());
        entities.forEach(entity -> {
            try {
                cardApprovalRepository.save(entity);
            } catch (DataIntegrityViolationException e) {
                // 이미 존재하는 승인번호 -> 무시
            }
        });
    }

    @Transactional(readOnly = true)
    public CategorySummaryResponse getCategorySummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate fourWeeksAgo = weekStart.minusWeeks(4);

        List<CategorySum> thisWeekRaw = cardApprovalRepository
            .sumByCategoryBetween(user.getId(), weekStart.atStartOfDay(), today.atTime(23,59,59));
        Map<String, Long> thisWeek = (thisWeekRaw != null ? thisWeekRaw : Collections.<CategorySum>emptyList())
            .stream()
            .collect(Collectors.toMap(CategorySum::getCategory, CategorySum::getTotal,
                (a, b) -> a));

        long actualWeeks = ChronoUnit.WEEKS.between(fourWeeksAgo, weekStart);
        long divisor = Math.max(1, actualWeeks);
        List<CategorySum> baselineRaw = cardApprovalRepository
            .sumByCategoryBetween(user.getId(), fourWeeksAgo.atStartOfDay(), weekStart.atStartOfDay());
        Map<String, Long> fourWeekTotal = (baselineRaw != null ? baselineRaw : Collections.<CategorySum>emptyList())
            .stream()
            .collect(Collectors.toMap(CategorySum::getCategory, s -> s.getTotal() / divisor,
                (a, b) -> a));

        Map<String, Double> overageRate = new HashMap<>();
        for (String cat : thisWeek.keySet()) {
            long baseline = fourWeekTotal.getOrDefault(cat, 0L);
            if (baseline > 0) {
                overageRate.put(cat, (thisWeek.get(cat) - baseline) * 100.0 / baseline);
            }
        }

        Integer quintile = user.getIncomeQuintile();
        double threshold = OVERAGE_THRESHOLD.getOrDefault(
            quintile != null ? quintile : 3, 0.25) * 100;

        return new CategorySummaryResponse(
            userId.toString(), weekStart.toString(),
            thisWeek, fourWeekTotal, overageRate,
            quintile != null ? quintile : 3,
            threshold
        );
    }

    @Transactional(readOnly = true)
    public ThresholdResponse getThreshold(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        Integer quintile = user.getIncomeQuintile();
        double threshold = OVERAGE_THRESHOLD.getOrDefault(
            quintile != null ? quintile : 3, 0.25);
        return new ThresholdResponse(
            user.getIncomeQuintile(),
            threshold * 100,
            user.getDisposableIncome(),
            user.getDailyAvailable(),
            "통계청 2024 Q4 가계동향조사 소득분위별 임계치",
            "2~4분위는 추정값. KOSIS 실제값 및 실험 결과 후 조정 예정"
        );
    }

    public String classifyMerchant(String merchantName) {
        return merchantMappingRepository
            .findByMerchantName(merchantName)
            .map(MerchantCategoryMapping::getCategory)
            .orElse("UNKNOWN");
    }

    private LocalDateTime parseDateTime(String dtime) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return LocalDateTime.parse(dtime, fmt);
    }
}
