package com.im.agentic.api.user.service;

import com.im.agentic.api.user.dto.UserResponseDto.ProfileResponse;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public ProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        return ProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .monthlyIncome(user.getMonthlyIncome())
                .fixedExpense(user.getFixedExpense())
                .disposableIncome(user.getDisposableIncome())
                .dailyAvailable(user.getDailyAvailable())
                .incomeQuintile(user.getIncomeQuintile())
                .build();
    }
}
