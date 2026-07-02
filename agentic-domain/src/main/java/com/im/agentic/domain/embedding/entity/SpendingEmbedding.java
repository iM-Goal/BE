package com.im.agentic.domain.embedding.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spending_embeddings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SpendingEmbedding extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    // pgvector 타입 — 768차원 (nomic-embed-text 기본값)
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private String embedding; // JSON 배열 문자열로 저장 후 DB에서 캐스팅

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 임베딩 원본 텍스트
}
