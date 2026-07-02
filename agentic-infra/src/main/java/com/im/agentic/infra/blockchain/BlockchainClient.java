package com.im.agentic.infra.blockchain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BlockchainClient {

    private final WebClient webClient;

    public BlockchainClient(@Value("${fastapi.base-url:http://localhost:8000}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String signContract(Long userId, Long goalId, List<Map<String, Object>> conditions) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "userId", userId,
                    "goalId", goalId,
                    "conditions", conditions
            );

            Map<?, ?> response = webClient.post()
                    .uri("/mock/blockchain/contract/sign")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.get("contractId") instanceof String contractId) {
                return contractId;
            }
            return null;
        } catch (Exception e) {
            log.error("[BlockchainClient] Error signing contract: {}", e.getMessage());
            return null;
        }
    }

    public boolean releaseContract(String contractId) {
        try {
            Map<?, ?> response = webClient.delete()
                    .uri("/mock/blockchain/contract/{contractId}", contractId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && "RELEASED".equals(response.get("status"))) {
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("[BlockchainClient] Error releasing contract: {}", e.getMessage());
            return false;
        }
    }
}
