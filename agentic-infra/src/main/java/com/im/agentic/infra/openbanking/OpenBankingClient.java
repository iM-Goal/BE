package com.im.agentic.infra.openbanking;

import com.im.agentic.infra.openbanking.dto.SpendingRecord;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class OpenBankingClient {

    private final WebClient webClient;

    public OpenBankingClient(@Value("${fastapi.base-url:http://localhost:8000}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SpendingResponse {
        private Long userId;
        private List<SpendingRecord> spending;
    }

    public List<SpendingRecord> getSpendingHistory(Long userId) {
        try {
            SpendingResponse response = webClient.get()
                    .uri("/mock/spending/{userId}", userId)
                    .retrieve()
                    .bodyToMono(SpendingResponse.class)
                    .block();
            return response != null ? response.getSpending() : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public boolean transfer(Long userId, Long goalId, Long amount) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "goalId", goalId,
                    "amount", amount
            );

            Map<?, ?> response = webClient.post()
                    .uri("/mock/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.get("success") instanceof Boolean success) {
                return success;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
