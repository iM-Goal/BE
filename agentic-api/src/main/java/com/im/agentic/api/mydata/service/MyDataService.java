package com.im.agentic.api.mydata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.mydata.dto.MyDataCacheResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
}
