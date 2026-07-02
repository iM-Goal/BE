package com.im.agentic.domain.mydata.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import com.im.agentic.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "my_data_caches")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MyDataCache extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_expenses", columnDefinition = "jsonb", nullable = false)
    private String categoryExpenses; // JSON representing monthly expenses per category

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weekly_avg_expenses", columnDefinition = "jsonb", nullable = false)
    private String weeklyAvgExpenses;

    @Column(name = "daily_available_amount", nullable = false)
    private Long dailyAvailableAmount;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;

    public void update(String categoryExpenses, String weeklyAvgExpenses, Long dailyAvailableAmount) {
        this.categoryExpenses = categoryExpenses;
        this.weeklyAvgExpenses = weeklyAvgExpenses;
        this.dailyAvailableAmount = dailyAvailableAmount;
        this.lastUpdatedAt = java.time.LocalDateTime.now();
    }
}
