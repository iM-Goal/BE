package com.im.agentic.api.programmable.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.infra.blockchain.StablecoinClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ProgrammableMoneyService {

    private final StablecoinClient stablecoinClient;
    private final GoalRepository goalRepository;
    private final ObjectMapper objectMapper;

    // 저축 이체 시 전체 플로우:
    // 원화 → iMKRW 발행 → Kaia 잠금
    @Transactional
    public ProgrammableTransferResult transferWithToken(Long userId, Long goalId, Long amount) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        // Step 1: 원화 → iMKRW 발행
        StablecoinClient.TokenMintResult mintResult = stablecoinClient.mintToken(userId, amount);
        if (!mintResult.success()) {
            log.error("[ProgrammableMoney] iMKRW 발행 실패: userId={}, amount={}", userId, amount);
            return new ProgrammableTransferResult(false, amount, 0L, null, "iMKRW 발행 실패");
        }
        log.info("[ProgrammableMoney] iMKRW 발행 완료: {}iMKRW, txHash={}", mintResult.mintedAmount(), mintResult.txHash());

        // Step 2: 출금 조건 파싱
        List<Map<String, Object>> conditions = parseConditions(goal.getWithdrawalCondition());

        // Step 3: Kaia 스마트컨트랙트 잠금
        String lockId = stablecoinClient.lockToken(userId, goalId, mintResult.mintedAmount(), conditions);
        if (lockId != null) {
            goal.setLockId(lockId);
            log.info("[ProgrammableMoney] Kaia 잠금 완료: lockId={}", lockId);
        }

        return new ProgrammableTransferResult(
                true, amount, mintResult.mintedAmount(), lockId,
                String.format("%d원 → %d iMKRW 발행 및 스마트컨트랙트 잠금 완료", amount, mintResult.mintedAmount())
        );
    }

    // Buy-out 플로우:
    // Kaia 잠금 해제 → iMKRW 소각 → 원화 출금 → DAGAON 지갑
    @Transactional
    public BuyOutTokenResult buyOutWithToken(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        Long totalAmount = goal.getCurrentAmount();
        String lockId = goal.getLockId();

        // Step 1: Kaia 스마트컨트랙트 잠금 해제
        if (lockId != null) {
            boolean unlocked = stablecoinClient.unlockToken(lockId);
            log.info("[ProgrammableMoney] Kaia 잠금 해제: lockId={}, success={}", lockId, unlocked);
        }

        // Step 2: iMKRW 소각
        boolean burned = stablecoinClient.burnToken(userId, totalAmount);
        log.info("[ProgrammableMoney] iMKRW 소각: amount={}, success={}", totalAmount, burned);

        // Step 3: DAGAON 지갑 출금
        String purpose = goal.getItemName() + " 구매";
        boolean withdrawn = stablecoinClient.withdrawToWallet(userId, totalAmount, purpose);
        log.info("[ProgrammableMoney] DAGAON 출금: amount={}, purpose={}, success={}", totalAmount, purpose, withdrawn);

        return new BuyOutTokenResult(
                withdrawn,
                totalAmount,
                goal.getItemName(),
                String.format("목표 달성! %d iMKRW 소각 완료. DAGAON 지갑으로 %d원 출금. %s을(를) 구매하세요!",
                        totalAmount, totalAmount, goal.getItemName())
        );
    }

    // iMKRW 잔액 조회
    public TokenBalanceResponse getTokenBalance(Long userId) {
        StablecoinClient.TokenBalanceResult balance = stablecoinClient.getBalance(userId);
        return new TokenBalanceResponse(
                balance.balance(),
                balance.lockedAmount(),
                balance.availableAmount()
        );
    }

    private List<Map<String, Object>> parseConditions(String withdrawalCondition) {
        if (withdrawalCondition == null) return Collections.emptyList();
        try {
            Map<String, Object> conditionMap = objectMapper.readValue(
                    withdrawalCondition, new TypeReference<>() {});
            Object conditions = conditionMap.get("conditions");
            if (conditions instanceof List<?>) {
                return (List<Map<String, Object>>) conditions;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[ProgrammableMoney] 출금 조건 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // DTO records
    public record ProgrammableTransferResult(
            boolean success, Long wonAmount, Long tokenAmount, String lockId, String message) {}

    public record BuyOutTokenResult(
            boolean success, Long amount, String itemName, String message) {}

    public record TokenBalanceResponse(
            Long balance, Long lockedAmount, Long availableAmount) {}
}
