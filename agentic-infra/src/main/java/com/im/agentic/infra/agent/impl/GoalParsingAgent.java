package com.im.agentic.infra.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.infra.agent.Agent;
import com.im.agentic.infra.agent.dto.AgentInput;
import com.im.agentic.infra.agent.dto.GoalParsingOutput;
import com.im.agentic.infra.rag.RagPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class GoalParsingAgent implements Agent<AgentInput, GoalParsingOutput> {

    private final RagPipelineService ragPipelineService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() { return "GoalParsingAgent"; }

    @Override
    public GoalParsingOutput execute(AgentInput input) {
        String systemPrompt = """
                당신은 금융 목표 파싱 AI입니다.
                사용자의 입력에서 물품명과 가격을 추출해 반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 출력하지 마세요.
                {"itemName": "물품명", "price": 숫자, "category": "카테고리"}
                가격을 모르면 price를 0으로 설정하세요.
                """;

        String rawResponse = ragPipelineService.generateWithRag(
                input.getUserId(), input.getRawInput(), systemPrompt);

        try {
            String cleaned = cleanJson(rawResponse);
            var map = objectMapper.readValue(cleaned, java.util.Map.class);
            return GoalParsingOutput.builder()
                    .itemName((String) map.get("itemName"))
                    .price(((Number) map.getOrDefault("price", 0)).longValue())
                    .category((String) map.getOrDefault("category", "기타"))
                    .build();
        } catch (Exception e) {
            log.error("[GoalParsingAgent] 파싱 실패: {}", e.getMessage());
            return GoalParsingOutput.builder()
                    .itemName(input.getRawInput())
                    .price(0L)
                    .category("기타")
                    .build();
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
