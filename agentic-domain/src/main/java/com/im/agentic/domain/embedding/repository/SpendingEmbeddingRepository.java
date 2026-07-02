package com.im.agentic.domain.embedding.repository;

import com.im.agentic.domain.embedding.entity.SpendingEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SpendingEmbeddingRepository extends JpaRepository<SpendingEmbedding, Long> {

    List<SpendingEmbedding> findByUserId(Long userId);

    // pgvector 코사인 유사도 검색 — 가장 유사한 소비 내역 Top-K 반환
    @Query(value = """
        SELECT * FROM spending_embeddings
        WHERE user_id = :userId
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<SpendingEmbedding> findSimilarByUserId(
            @Param("userId") Long userId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("topK") int topK
    );
}
