package com.im.agentic.domain.transaction.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Transaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "merchant")
    private String merchant;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private SpendingCategory category;

    @Column(name = "transacted_at", nullable = false)
    private LocalDateTime transactedAt;
}
