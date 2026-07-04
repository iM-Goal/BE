package com.im.agentic.api.mydata.service;

import com.im.agentic.api.mydata.dto.UserFinancialRequest;
import com.im.agentic.api.mydata.dto.UserFinancialResponse;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFinancialService {

    private final UserRepository userRepository;

    // 출처: 통계청 2024년 4분기 가계동향조사 (2025.02.27 발표)
    // 비소비지출 월평균 100만 8천원 / 전체 월소득 521만 5천원 = 19.3%
    private static final double NONCONSUMPTION_RATIO = 0.193;

    public int calculateIncomeQuintile(long monthlyIncome) {
        if (monthlyIncome < 1_213_000)  return 1;  // [실제값]
        if (monthlyIncome < 2_910_000)  return 2;  // [추정값]
        if (monthlyIncome < 4_406_000)  return 3;  // [추정값]
        if (monthlyIncome < 6_342_000)  return 4;  // [추정값]
        return 5;                                   // [실제값]
    }

    @Transactional
    public UserFinancialResponse updateFinancialInfo(Long userId, UserFinancialRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        long nonconsumption = (long)(req.monthlyIncome() * NONCONSUMPTION_RATIO);
        long disposable = Math.max(0, req.monthlyIncome() - nonconsumption - req.monthlyFixedCost());
        int quintile = calculateIncomeQuintile(req.monthlyIncome());

        user.updateFinancialInfo(
            req.monthlyIncome(),
            req.monthlyFixedCost(),
            quintile,
            disposable,
            disposable / 30
        );
        userRepository.save(user);

        return new UserFinancialResponse(
            req.monthlyIncome(), req.monthlyFixedCost(),
            disposable, disposable / 30, quintile,
            "통계청 2024 Q4 가계동향조사 기준"
        );
    }

    @Transactional(readOnly = true)
    public UserFinancialResponse getFinancialInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        return new UserFinancialResponse(
            user.getMonthlyIncome(), user.getFixedExpense(), // fixedExpense 컬럼 재사용
            user.getDisposableIncome(), user.getDailyAvailable(),
            user.getIncomeQuintile(), "통계청 2024 Q4 가계동향조사 기준"
        );
    }
}
