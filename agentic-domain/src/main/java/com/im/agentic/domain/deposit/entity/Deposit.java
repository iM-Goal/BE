package com.im.agentic.domain.deposit.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "deposits")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Deposit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "imkrw_amount", nullable = false)
    private Long imkrwAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DepositStatus status;

    @Column(name = "lock_tx_hash")
    private String lockTxHash;

    @Column(name = "release_tx_hash")
    private String releaseTxHash;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    public void release(String releaseTxHash) {
        this.status = DepositStatus.RELEASED;
        this.releaseTxHash = releaseTxHash;
        this.releasedAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = DepositStatus.WITHDRAWN;
        this.releasedAt = LocalDateTime.now();
    }
}
