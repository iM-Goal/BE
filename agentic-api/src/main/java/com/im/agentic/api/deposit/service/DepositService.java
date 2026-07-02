package com.im.agentic.api.deposit.service;

import com.im.agentic.api.deposit.dto.DepositResponseDto;
import com.im.agentic.api.deposit.dto.WithdrawPreviewResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.deposit.entity.Deposit;
import com.im.agentic.domain.deposit.entity.DepositStatus;
import com.im.agentic.domain.deposit.repository.DepositRepository;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.blockchain.StablecoinClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DepositService {

    private final DepositRepository depositRepository;
    private final UserRepository userRepository;
    private final StablecoinClient stablecoinClient;
    private final StringRedisTemplate redisTemplate;

    // 보증금 현황 조회
    public List<DepositResponseDto> getDeposits(Long userId) {
        return depositRepository.findByUserId(userId)
                .stream()
                .map(DepositResponseDto::from)
                .collect(Collectors.toList());
    }

    // 출금 미리보기
    public WithdrawPreviewResponseDto getWithdrawPreview(Long userId, Long depositId) {
        Deposit deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> new AgenticException(ErrorCode.DEPOSIT_NOT_FOUND));

        if (!deposit.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.UNAUTHORIZED);
        }
        if (deposit.getStatus() != DepositStatus.LOCKED) {
            throw new AgenticException(ErrorCode.DEPOSIT_NOT_LOCKED);
        }

        Goal goal = deposit.getMission().getGoal();
        long goalDelayDays = 0;
        if (goal != null && goal.getDailyBudget() != null && goal.getDailyBudget() > 0) {
            goalDelayDays = deposit.getAmount() / goal.getDailyBudget();
        }

        // 4자리 랜덤 숫자 생성
        Random random = new Random();
        String confirmCode = String.format("%04d", random.nextInt(10000));

        // Redis에 5분 TTL 저장
        String key = "deposit:confirm:" + depositId + ":" + userId;
        redisTemplate.opsForValue().set(key, confirmCode, Duration.ofMinutes(5));

        return WithdrawPreviewResponseDto.builder()
                .depositId(depositId)
                .goalDelayDays(goalDelayDays)
                .confirmCode(confirmCode)
                .build();
    }

    // 출금 실행 (마찰 절차)
    @Transactional
    public void withdraw(Long userId, Long depositId, String reason, String confirmCode) {
        String key = "deposit:confirm:" + depositId + ":" + userId;
        String cachedCode = redisTemplate.opsForValue().get(key);

        if (cachedCode == null) {
            throw new AgenticException(ErrorCode.CONFIRM_CODE_EXPIRED);
        }
        if (!cachedCode.equals(confirmCode)) {
            throw new AgenticException(ErrorCode.WRONG_CONFIRM_CODE);
        }

        Deposit deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> new AgenticException(ErrorCode.DEPOSIT_NOT_FOUND));

        if (!deposit.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.UNAUTHORIZED);
        }
        if (deposit.getStatus() != DepositStatus.LOCKED) {
            throw new AgenticException(ErrorCode.DEPOSIT_NOT_LOCKED);
        }

        // iMKRW 소각 및 Kaia 출금 API 요청
        stablecoinClient.burnToken(userId, deposit.getImkrwAmount());
        stablecoinClient.withdrawDeposit(deposit.getLockTxHash(), reason);

        // Deposit 상태 변경
        deposit.withdraw();

        // 캐시 삭제
        redisTemplate.delete(key);
    }
}
