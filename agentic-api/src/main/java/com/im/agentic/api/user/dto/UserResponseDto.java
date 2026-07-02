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
}
