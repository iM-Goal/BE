package com.im.agentic.api.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class UserResponseDto {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private Long userId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ProfileResponse {
        private Long userId;
        private String email;
        private String nickname;
        private Long monthlyIncome;
        private Long fixedExpense;
        private Long disposableIncome;
        private Long dailyAvailable;
        private Integer incomeQuintile;
    }
}
