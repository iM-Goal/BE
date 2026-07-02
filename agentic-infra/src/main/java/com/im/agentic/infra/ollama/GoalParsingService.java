package com.im.agentic.infra.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalParsingService {

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public record GoalParsingResult(String itemName, Long price) {}

    public GoalParsingResult parseGoal(String rawInput) {
        String prompt = """
                다음 입력에서 물품명과 가격을 추출해서 반드시 아래 JSON 형식으로만 응답해. 다른 텍스트는 절대 포함하지 마.
                입력: %s
                응답 형식: {"itemName": "물품명", "price": 숫자}
                가격을 모르면 price를 0으로 설정해.
                """.formatted(rawInput);

        try {
            String rawResponse = ollamaClient.generate(prompt);
            log.info("[GoalParsing] Ollama Raw Response: {}", rawResponse);
            String jsonStr = cleanJson(rawResponse);
            return objectMapper.readValue(jsonStr, GoalParsingResult.class);
        } catch (Exception e) {
            log.error("[GoalParsing] Parsing error: {}", e.getMessage());
            // Fallback in case of parse error
            return new GoalParsingResult("Unknown Item", 0L);
        }
    }

    private String cleanJson(String response) {
        if (response == null) return "{}";
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        return response.trim();
    }
}
