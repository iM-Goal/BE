package com.im.agentic.infra.rag;

import com.im.agentic.domain.embedding.entity.MissionCase;
import com.im.agentic.domain.embedding.entity.SpendingEmbedding;
import com.im.agentic.domain.embedding.repository.MissionCaseRepository;
import com.im.agentic.domain.embedding.repository.SpendingEmbeddingRepository;
import com.im.agentic.infra.ollama.EmbeddingClient;
import com.im.agentic.infra.ollama.OllamaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class RagPipelineService {

    private final EmbeddingClient embeddingClient;
    private final OllamaClient ollamaClient;
    private final SpendingEmbeddingRepository spendingEmbeddingRepository;
    private final MissionCaseRepository missionCaseRepository;

    // 핵심 RAG 파이프라인:
    // 1. 쿼리 임베딩 생성
    // 2. pgvector에서 유사 소비 내역 검색
    // 3. 유사 미션 사례 검색
    // 4. 컨텍스트 주입된 프롬프트 생성
    // 5. Ollama 추론
    public String generateWithRag(Long userId, String query, String systemPrompt) {
        try {
            // Step 1: 쿼리 임베딩
            List<Double> queryEmbedding = embeddingClient.embed(query);
            String queryVector = embeddingClient.toVectorString(queryEmbedding);

            // Step 2: 유사 소비 내역 검색 (Top-5)
            List<SpendingEmbedding> similarSpendings = spendingEmbeddingRepository
                    .findSimilarByUserId(userId, queryVector, 5);

            // Step 3: 유사 성공 미션 사례 검색 (Top-3)
            List<MissionCase> similarCases = missionCaseRepository
                    .findSimilarCases(queryVector, 3);

            // Step 4: 컨텍스트 구성
            String spendingContext = similarSpendings.stream()
                    .map(s -> String.format("- %s %s: %d원", s.getCategory(), s.getMerchantName(), s.getAmount()))
                    .collect(Collectors.joining("\n"));

            String missionContext = similarCases.stream()
                    .map(c -> String.format("- [%s] %s 카테고리, 성공률 %.0f%%",
                            c.getMissionType(), c.getCategory(), c.getSuccessRate() * 100))
                    .collect(Collectors.joining("\n"));

            // Step 5: 컨텍스트 주입 프롬프트
            String ragPrompt = String.format("""
                    %s
                    
                    [참고: 사용자의 유사 소비 내역]
                    %s
                    
                    [참고: 성공률 높은 유사 미션 사례]
                    %s
                    
                    [질문]
                    %s
                    """, systemPrompt, spendingContext, missionContext, query);

            log.info("[RAG] 컨텍스트 주입 완료. 소비내역 {}건, 미션사례 {}건",
                    similarSpendings.size(), similarCases.size());

            // Step 6: Ollama 추론
            return ollamaClient.generate(ragPrompt);

        } catch (Exception e) {
            log.error("[RAG] 파이프라인 실패, 기본 추론으로 폴백: {}", e.getMessage());
            // 폴백: RAG 없이 직접 Ollama 호출
            return ollamaClient.generate(systemPrompt + "\n" + query);
        }
    }

    // 소비 내역 벡터화 후 pgvector에 저장
    public void embedAndSaveSpending(Long userId, String category,
                                      String merchantName, Long amount) {
        String content = String.format("%s %s %d원", category, merchantName, amount);
        List<Double> embedding = embeddingClient.embed(content);
        if (embedding.isEmpty()) return;

        SpendingEmbedding entity = SpendingEmbedding.builder()
                .userId(userId)
                .category(category)
                .merchantName(merchantName)
                .amount(amount)
                .embedding(embeddingClient.toVectorString(embedding))
                .content(content)
                .build();

        spendingEmbeddingRepository.save(entity);
        log.info("[RAG] 소비내역 임베딩 저장: userId={}, content={}", userId, content);
    }

    // 성공 미션 사례 벡터화 후 저장
    public void embedAndSaveMissionCase(String category, String missionType,
                                         Long limitAmount, Long transferAmount,
                                         Double successRate) {
        String content = String.format("%s 카테고리 %s 미션, 한도 %d원, 성공률 %.0f%%",
                category, missionType, limitAmount != null ? limitAmount : 0, successRate * 100);
        List<Double> embedding = embeddingClient.embed(content);
        if (embedding.isEmpty()) return;

        MissionCase missionCase = MissionCase.builder()
                .category(category)
                .missionType(missionType)
                .limitAmount(limitAmount)
                .transferAmount(transferAmount)
                .successRate(successRate)
                .embedding(embeddingClient.toVectorString(embedding))
                .content(content)
                .build();

        missionCaseRepository.save(missionCase);
    }
}
