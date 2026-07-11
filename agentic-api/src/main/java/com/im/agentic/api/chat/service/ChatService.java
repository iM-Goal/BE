package com.im.agentic.api.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.chat.dto.ChatResponseDto.ChatResponse;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.deposit.entity.Deposit;
import com.im.agentic.domain.deposit.repository.DepositRepository;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.mission.repository.MissionRepository;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentClient;
import com.im.agentic.infra.agent.dto.ChatOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ChatService {

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final MissionRepository missionRepository;
    private final DepositRepository depositRepository;
    private final MyDataCacheRepository myDataCacheRepository;
    private final AgentClient agentClient;
    private final ObjectMapper objectMapper;

    public ChatResponse chat(Long userId, String message) {
        log.info("[ChatService] userId={}, message={}", userId, message);

        // 1. 유저 재정정보
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        Map<String, Object> financialInfo = new HashMap<>();
        financialInfo.put("monthlyIncome",      nullSafe(user.getMonthlyIncome()));
        financialInfo.put("disposableIncome",   nullSafe(user.getDisposableIncome()));
        financialInfo.put("dailyAvailable",     nullSafe(user.getDailyAvailable()));
        financialInfo.put("incomeQuintile",     user.getIncomeQuintile() != null ? user.getIncomeQuintile() : 3);

        // 2. 목표 현황
        List<Map<String, Object>> goals = goalRepository.findByUserId(userId).stream()
                .map(g -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("slotType",                g.getSlotType().name());
                    m.put("itemName",                g.getItemName());
                    m.put("targetAmount",            g.getTargetAmount());
                    m.put("currentAmount",           g.getCurrentAmount());
                    m.put("achievementRate",         g.getAchievementRate());
                    m.put("estimatedCompletionDate", g.getEstimatedCompletionDate() != null
                            ? g.getEstimatedCompletionDate().toString() : null);
                    return m;
                })
                .toList();

        // 3. 미션 현황
        List<Map<String, Object>> missions = missionRepository.findByUserId(userId).stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("status",      m.getStatus().name());
                    map.put("category",    m.getCategory() != null ? m.getCategory().name() : null);
                    map.put("limitAmount", m.getLimitAmount());
                    map.put("description", m.getDescription());
                    return map;
                })
                .toList();

        // 4. 보증금 총액
        long totalDeposit = depositRepository.findByUserId(userId).stream()
                .mapToLong(Deposit::getAmount)
                .sum();

        // 5. 마이데이터 (카테고리별 지출)
        Map<String, Integer> categoryExpenses = new HashMap<>();
        myDataCacheRepository.findByUserId(userId).ifPresent(cache -> {
            try {
                Map<String, Integer> parsed = objectMapper.readValue(
                        cache.getCategoryExpenses(), new TypeReference<>() {});
                categoryExpenses.putAll(parsed);
            } catch (Exception e) {
                log.warn("[ChatService] 카테고리 지출 파싱 실패: {}", e.getMessage());
            }
        });

        // 6. 컨텍스트 조합 → AgentClient 호출
        Map<String, Object> context = new HashMap<>();
        context.put("financialInfo",     financialInfo);
        context.put("goals",             goals);
        context.put("missions",          missions);
        context.put("totalDeposit",      totalDeposit);
        context.put("categoryExpenses",  categoryExpenses);

        ChatOutput output = agentClient.chat(userId, message, context);
        return ChatResponse.builder()
                .answer(output.getAnswer())
                .build();
    }

    private long nullSafe(Long value) {
        return value != null ? value : 0L;
    }
}
