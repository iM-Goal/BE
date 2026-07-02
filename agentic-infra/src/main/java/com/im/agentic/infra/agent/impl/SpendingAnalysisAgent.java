package com.im.agentic.infra.agent.impl;

import com.im.agentic.infra.agent.Agent;
import com.im.agentic.infra.agent.dto.AgentInput;
import com.im.agentic.infra.openbanking.OpenBankingClient;
import com.im.agentic.infra.openbanking.dto.SpendingRecord;
import com.im.agentic.infra.rag.RagPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class SpendingAnalysisAgent implements Agent<AgentInput, Map<String, Long>> {

    private final OpenBankingClient openBankingClient;
    private final RagPipelineService ragPipelineService;

    @Override
    public String getName() { return "SpendingAnalysisAgent"; }

    @Override
    public Map<String, Long> execute(AgentInput input) {
        // 1. 마이데이터에서 소비 내역 수집
        List<SpendingRecord> records = openBankingClient.getSpendingHistory(input.getUserId());

        // 2. 카테고리별 집계
        Map<String, Long> categoryExpenses = records.stream()
                .collect(Collectors.groupingBy(
                        SpendingRecord::getCategory,
                        Collectors.summingLong(SpendingRecord::getAmount)
                ));

        // 3. 소비 내역을 pgvector에 임베딩 저장 (RAG용 데이터 축적)
        records.forEach(record ->
                ragPipelineService.embedAndSaveSpending(
                        input.getUserId(),
                        record.getCategory(),
                        record.getMerchantName(),
                        record.getAmount()
                )
        );

        log.info("[SpendingAnalysisAgent] userId={}, 카테고리 {}개 분석 완료",
                input.getUserId(), categoryExpenses.size());
        return categoryExpenses;
    }
}
