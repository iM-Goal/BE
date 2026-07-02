package com.im.agentic.domain.mission.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "missions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Mission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_source", nullable = false)
    private MissionSource missionSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private SpendingCategory category;

    @Column(name = "description")
    private String description;

    @Column(name = "limit_amount")
    private Long limitAmount;

    @Column(name = "deposit_amount")
    private Long depositAmount;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "overage_rate")
    private Double overageRate;

    @Column(name = "proposal_reason", columnDefinition = "TEXT")
    private String proposalReason;

    public void accept() {
        this.status = MissionStatus.IN_PROGRESS;
        this.startDate = LocalDate.now();
        this.endDate = LocalDate.now().plusDays(this.durationDays != null ? this.durationDays : 3);
    }

    public void succeed() {
        this.status = MissionStatus.SUCCESS;
    }

    public void fail() {
        this.status = MissionStatus.FAILED;
    }
}
