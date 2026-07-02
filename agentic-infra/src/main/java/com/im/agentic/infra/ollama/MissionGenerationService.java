package com.im.agentic.infra.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionGenerationService {

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public record MissionGenerationResult(
            String restraintCategory,
            Long restraintLimitAmount,
            Double restraintContributionRate,
            Long savingTransferAmount,
            Double savingContributionRate
    ) {}

    @Getter
    @Setter
    public static class OllamaMissionWrapper {
        private RestraintMission restraint;
        private SavingMission saving;
    }

    @Getter
    @Setter
    public static class RestraintMission {
        private String categoryName;
        private Long limitAmount;
        private Double contributionRate;
    }

    @Getter
    @Setter
    public static class SavingMission {
        private Long transferAmount;
        private Double contributionRate;
    }

    public MissionGenerationResult generateMissions(String categoryExpensesJson, Long dailyAvailableAmount, String goalItemName) {
        String prompt = """
                당신은 절약 미션을 생성하는 AI입니다.
                사용자의 소비 패턴: %s
                오늘 가용 금액: %d원
                저축 목표: %s
                
                아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.
                {
                  "restraint": {
                    "categoryName": "카테고리명",
                    "limitAmount": 한도금액(숫자),
                    "contributionRate": 기여율(0.0~100.0 사이 소수)
                  },
                  "saving": {
                    "transferAmount": 이체금액(숫자),
                    "contributionRate": 기여율(0.0~100.0 사이 소수)
                  }
                }
                """.formatted(categoryExpensesJson, dailyAvailableAmount, goalItemName);

        try {
            String rawResponse = ollamaClient.generate(prompt);
            log.info("[MissionGen] Ollama Raw Response: {}", rawResponse);
            String jsonStr = cleanJson(rawResponse);
            OllamaMissionWrapper wrapper = objectMapper.readValue(jsonStr, OllamaMissionWrapper.class);

            return new MissionGenerationResult(
                    wrapper.getRestraint().getCategoryName(),
                    wrapper.getRestraint().getLimitAmount(),
                    wrapper.getRestraint().getContributionRate(),
                    wrapper.getSaving().getTransferAmount(),
                    wrapper.getSaving().getContributionRate()
            );
        } catch (Exception e) {
            log.error("[MissionGen] Parsing error: {}", e.getMessage());
            // Fallback default values
            return new MissionGenerationResult(
                    "식비", 10000L, 1.0,
                    5000L, 0.5
            );
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
