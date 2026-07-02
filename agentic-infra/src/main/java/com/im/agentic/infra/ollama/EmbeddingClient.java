package com.im.agentic.infra.ollama;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@Deprecated
public class EmbeddingClient {

    private final WebClient webClient;

    public EmbeddingClient(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // nomic-embed-text로 텍스트 임베딩 생성
    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "model", "nomic-embed-text",
                    "prompt", text
            );

            Map<?, ?> response = webClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && response.get("embedding") instanceof List<?> embedding) {
                return (List<Double>) embedding;
            }
            return List.of();
        } catch (Exception e) {
            log.error("[EmbeddingClient] 임베딩 생성 실패: {}", e.getMessage());
            return List.of();
        }
    }

    // List<Double> → pgvector 포맷 문자열 "[0.1, 0.2, ...]"
    public String toVectorString(List<Double> embedding) {
        return embedding.toString();
    }
}
