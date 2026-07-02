package com.im.agentic.infra.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.infra.agent.Agent;
import com.im.agentic.infra.agent.dto.AgentInput;
import com.im.agentic.infra.agent.dto.MissionGenerationOutput;
import com.im.agentic.infra.rag.RagPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class MissionGenerationAgent implements Agent<AgentInput, MissionGenerationOutput> {

    private final RagPipelineService ragPipelineService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() { return "MissionGenerationAgent"; }

    @Override
    public MissionGenerationOutput execute(AgentInput input) {
        String systemPrompt = """
                당신은 금융 미션 생성 AI입니다.
                사용자의 소비 패턴을 분석해 절약 미션을 생성합니다. 다른 텍스트는 절대 포함하지 말고 아래 JSON만 리턴하세요.
                반드시 아래 JSON 형식으로만 응답하세요.
                {
                  "restraint": {"categoryName": "카테고리명", "limitAmount": 숫자, "contributionRate": 소수},
                  "saving": {"transferAmount": 숫자, "contributionRate": 소수}
                }
                """;

        String query = String.format(
                "소비 패턴: %s, 가용금액: %d원, 목표: %s",
                input.getCategoryExpenses(),
                input.getDailyAvailableAmount(),
                input.getGoalItemName()
        );

        String rawResponse = ragPipelineService.generateWithRag(
                input.getUserId(), query, systemPrompt);

        try {
            String cleaned = cleanJson(rawResponse);
            Map<?, ?> wrapper = objectMapper.readValue(cleaned, Map.class);
            Map<?, ?> restraint = (Map<?, ?>) wrapper.get("restraint");
            Map<?, ?> saving = (Map<?, ?>) wrapper.get("saving");

            MissionGenerationOutput output = MissionGenerationOutput.builder()
                    .restraintCategory((String) restraint.get("categoryName"))
                    .restraintLimitAmount(((Number) restraint.get("limitAmount")).longValue())
                    .restraintContributionRate(((Number) restraint.get("contributionRate")).doubleValue())
                    .savingTransferAmount(((Number) saving.get("transferAmount")).longValue())
                    .savingContributionRate(((Number) saving.get("contributionRate")).doubleValue())
                    .build();

            // 성공한 미션 사례 pgvector에 저장 (RAG 데이터 축적)
            ragPipelineService.embedAndSaveMissionCase(
                    output.getRestraintCategory(), "RESTRAINT",
                    output.getRestraintLimitAmount(), null, 0.7);

            return output;
        } catch (Exception e) {
            log.error("[MissionGenerationAgent] 파싱 실패, 기본값 사용: {}", e.getMessage());
            return MissionGenerationOutput.builder()
                    .restraintCategory("식비").restraintLimitAmount(10000L)
                    .restraintContributionRate(1.0).savingTransferAmount(5000L)
                    .savingContributionRate(0.5).build();
        }
    }

    private String cleanJson(String response) {
        if (response == null) return "{}";
        response = response.trim();
        if (response.startsWith("```json")) response = response.substring(7);
        else if (response.startsWith("```")) response = response.substring(3);
        if (response.endsWith("```")) response = response.substring(0, response.length() - 3);
        return response.trim();
    }
}
