package com.im.agentic.infra.ollama;

import com.im.agentic.infra.ollama.dto.OllamaRequest;
import com.im.agentic.infra.ollama.dto.OllamaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

@Component
public class OllamaClient {

    private final WebClient webClient;

    public OllamaClient(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String generate(String prompt) {
        OllamaRequest request = OllamaRequest.builder()
                .model("qwen2.5")
                .prompt(prompt)
                .stream(false)
                .build();

        OllamaResponse response = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        return response != null ? response.getResponse() : "";
    }
}
