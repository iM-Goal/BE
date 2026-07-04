package com.im.agentic.infra.agent;

import com.im.agentic.infra.agent.dto.*;
import com.im.agentic.infra.openbanking.OpenBankingClient;
import com.im.agentic.infra.openbanking.dto.SpendingRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentOrchestrator {

    private final AgentClient agentClient;
    private final OpenBankingClient openBankingClient;

    // 목표 파싱
    public GoalParsingOutput parseGoal(Long userId, String rawInput, Integer incomeQuintile, Long dailyAvailable) {
        log.info("[Orchestrator] GoalParsingAgent 라우팅: userId={}, incomeQuintile={}, dailyAvailable={}", userId, incomeQuintile, dailyAvailable);
        return agentClient.parseGoal(userId, rawInput, incomeQuintile, dailyAvailable);
    }

    // 소비 분석 (OpenBanking에서 데이터 가져와서 Agent Server로 전달)
    public Map<String, Long> analyzeSpending(Long userId) {
        log.info("[Orchestrator] SpendingAnalysisAgent 라우팅: userId={}", userId);

        List<SpendingRecord> records = openBankingClient.getSpendingHistory(userId);
        List<Map<String, Object>> spendingData = records.stream()
                .map(r -> Map.<String, Object>of(
                        "category", r.getCategory(),
                        "amount", r.getAmount(),
                        "merchantName", r.getMerchantName(),
                        "date", r.getDate().toString()
                ))
                .collect(Collectors.toList());

        SpendingAnalysisOutput output = agentClient.analyzeSpending(userId, spendingData);
        return output.getCategoryExpenses();
    }

    // 미션 생성
    public MissionGenerationOutput generateMissions(Long userId, String categoryExpensesJson,
                                                     Long dailyAvailableAmount, String goalItemName) {
        log.info("[Orchestrator] MissionGenerationAgent 라우팅: userId={}", userId);

        // JSON 문자열 → Map 변환
        Map<String, Long> categoryExpenses;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            categoryExpenses = mapper.readValue(categoryExpensesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.error("[Orchestrator] categoryExpenses 파싱 실패: {}", e.getMessage());
            categoryExpenses = Map.of();
        }

        return agentClient.generateMission(userId, categoryExpenses, dailyAvailableAmount, goalItemName);
    }

    // 이체 실행
    public Boolean executeTransfer(Long userId, Long goalId, Long amount) {
        log.info("[Orchestrator] ExecutionAgent 라우팅: userId={}, goalId={}", userId, goalId);
        ExecutionOutput output = agentClient.execute(
                userId, goalId, amount, "SAVING", null, null, null);
        return output.isShouldTransfer();
    }

    // 미션 판단 실행 (RESTRAINT)
    public ExecutionOutput executeRestraintMission(Long userId, Long goalId,
                                                    String categoryName, Long limitAmount,
                                                    Long actualSpending) {
        log.info("[Orchestrator] ExecutionAgent(RESTRAINT) 라우팅: userId={}", userId);
        return agentClient.execute(userId, goalId, 0L, "RESTRAINT",
                categoryName, limitAmount, actualSpending);
    }
}
