package com.im.agentic.domain.goal.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import com.im.agentic.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "goals")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Goal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false)
    private GoalSlotType slotType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GoalStatus status;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    @Builder.Default
    @Column(name = "current_amount", nullable = false)
    private Long currentAmount = 0L;

    @Builder.Default
    @Column(name = "achievement_rate", nullable = false)
    private Double achievementRate = 0.0;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "item_image_url")
    private String itemImageUrl;

    @Column(name = "registration_type", nullable = false)
    private String registrationType; // e.g. "NATURAL_LANGUAGE", "URL", "SCREENSHOT"

    @Column(name = "estimated_completion_date")
    private LocalDate estimatedCompletionDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "withdrawal_condition", columnDefinition = "jsonb")
    private String withdrawalCondition;

    @Column(name = "lock_id")
    private String lockId;

    @Column(name = "daily_budget")
    private Long dailyBudget;

    @Column(name = "duration_days")
    private Integer durationDays;

    public void updateProgress(Long amount) {
        this.currentAmount += amount;
        this.achievementRate = (double) this.currentAmount / this.targetAmount * 100;
        if (this.achievementRate >= 100.0) {
            this.status = GoalStatus.COMPLETED;
        }
    }

    public void updateGoalDetails(String itemName, Long targetAmount, String itemImageUrl) {
        this.itemName = itemName;
        this.targetAmount = targetAmount;
        this.itemImageUrl = itemImageUrl;
    }

    public void cancel() {
        this.status = GoalStatus.CANCELLED;
    }

    public void complete() {
        this.status = GoalStatus.COMPLETED;
    }

    public void setWithdrawalCondition(String withdrawalCondition) {
        this.withdrawalCondition = withdrawalCondition;
    }

    public void setLockId(String lockId) {
        this.lockId = lockId;
    }
}

