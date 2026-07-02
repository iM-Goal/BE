package com.im.agentic.domain.embedding.repository;

import com.im.agentic.domain.embedding.entity.MissionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MissionCaseRepository extends JpaRepository<MissionCase, Long> {

    // 유사한 성공 미션 사례 Top-K 검색
    @Query(value = """
        SELECT * FROM mission_cases
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<MissionCase> findSimilarCases(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("topK") int topK
    );
}
