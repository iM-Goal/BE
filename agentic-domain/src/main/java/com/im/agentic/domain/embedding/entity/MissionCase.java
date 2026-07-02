package com.im.agentic.domain.embedding.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 성공한 미션 사례를 벡터화해서 저장 — RAG에서 유사 사례 검색용
@Entity
@Table(name = "mission_cases")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MissionCase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "mission_type", nullable = false)
    private String missionType; // RESTRAINT or SAVING

    @Column(name = "limit_amount")
    private Long limitAmount;

    @Column(name = "transfer_amount")
    private Long transferAmount;

    @Column(name = "success_rate", nullable = false)
    private Double successRate; // 해당 유형 미션의 성공률

    @Column(name = "embedding", columnDefinition = "vector(768)")
    private String embedding;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 임베딩 원본 텍스트
}
