package com.im.agentic.domain.user.entity;

import com.im.agentic.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "monthly_income")
    private Long monthlyIncome;

    @Column(name = "fixed_expense")
    private Long fixedExpense;

    @Column(name = "income_quintile")
    private Integer incomeQuintile;

    @Column(name = "disposable_income")
    private Long disposableIncome;

    @Column(name = "daily_available")
    private Long dailyAvailable;

    public void updateProfile(String nickname, Long monthlyIncome, Long fixedExpense) {
        this.nickname = nickname;
        this.monthlyIncome = monthlyIncome;
        this.fixedExpense = fixedExpense;
    }

    public void updateFinancialInfo(Long monthlyIncome, Long fixedExpense, Integer incomeQuintile, Long disposableIncome, Long dailyAvailable) {
        this.monthlyIncome = monthlyIncome;
        this.fixedExpense = fixedExpense;
        this.incomeQuintile = incomeQuintile;
        this.disposableIncome = disposableIncome;
        this.dailyAvailable = dailyAvailable;
    }
}
