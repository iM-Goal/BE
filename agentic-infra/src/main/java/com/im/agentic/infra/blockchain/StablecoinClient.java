package com.im.agentic.infra.blockchain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StablecoinClient {

    private final WebClient webClient;

    public StablecoinClient(@Value("${fastapi.base-url:http://localhost:8000}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // 원화 → iMKRW 발행
    public TokenMintResult mintToken(Long userId, Long amount) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "amount", amount
            );
            Map<?, ?> response = webClient.post()
                    .uri("/mock/imkrw/mint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("[StablecoinClient] iMKRW 발행 성공: userId={}, amount={}", userId, amount);
                return new TokenMintResult(true,
                        ((Number) response.get("mintedAmount")).longValue(),
                        (String) response.get("txHash"));
            }
            return new TokenMintResult(false, 0L, null);
        } catch (Exception e) {
            log.error("[StablecoinClient] iMKRW 발행 실패: {}", e.getMessage());
            return new TokenMintResult(false, 0L, null);
        }
    }

    // iMKRW → 원화 소각
    public boolean burnToken(Long userId, Long amount) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "amount", amount
            );
            Map<?, ?> response = webClient.post()
                    .uri("/mock/imkrw/burn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            log.info("[StablecoinClient] iMKRW 소각: userId={}, amount={}, success={}", userId, amount, success);
            return success;
        } catch (Exception e) {
            log.error("[StablecoinClient] iMKRW 소각 실패: {}", e.getMessage());
            return false;
        }
    }

    // Kaia 스마트컨트랙트에 토큰 잠금
    public String lockToken(Long userId, Long goalId, Long amount, List<Map<String, Object>> conditions) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "goalId", goalId,
                    "amount", amount,
                    "conditions", conditions
            );
            Map<?, ?> response = webClient.post()
                    .uri("/mock/kaia/lock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                String lockId = (String) response.get("lockId");
                log.info("[StablecoinClient] Kaia 잠금 성공: lockId={}", lockId);
                return lockId;
            }
            return null;
        } catch (Exception e) {
            log.error("[StablecoinClient] Kaia 잠금 실패: {}", e.getMessage());
            return null;
        }
    }

    // Kaia 스마트컨트랙트 잠금 해제
    public boolean unlockToken(String lockId) {
        try {
            Map<?, ?> response = webClient.post()
                    .uri("/mock/kaia/unlock/{lockId}", lockId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            boolean success = response != null && "UNLOCKED".equals(response.get("status"));
            log.info("[StablecoinClient] Kaia 잠금 해제: lockId={}, success={}", lockId, success);
            return success;
        } catch (Exception e) {
            log.error("[StablecoinClient] Kaia 잠금 해제 실패: {}", e.getMessage());
            return false;
        }
    }

    // DAGAON 지갑 출금
    public boolean withdrawToWallet(Long userId, Long amount, String purpose) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "amount", amount,
                    "purpose", purpose
            );
            Map<?, ?> response = webClient.post()
                    .uri("/mock/dagaon/withdraw")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            log.info("[StablecoinClient] DAGAON 출금: userId={}, amount={}, success={}", userId, amount, success);
            return success;
        } catch (Exception e) {
            log.error("[StablecoinClient] DAGAON 출금 실패: {}", e.getMessage());
            return false;
        }
    }

    // iMKRW 잔액 조회
    public TokenBalanceResult getBalance(Long userId) {
        try {
            Map<?, ?> response = webClient.get()
                    .uri("/mock/imkrw/balance/{userId}", userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                return new TokenBalanceResult(
                        ((Number) response.get("balance")).longValue(),
                        ((Number) response.get("lockedAmount")).longValue(),
                        ((Number) response.get("availableAmount")).longValue()
                );
            }
            return new TokenBalanceResult(0L, 0L, 0L);
        } catch (Exception e) {
            log.error("[StablecoinClient] 잔액 조회 실패: {}", e.getMessage());
            return new TokenBalanceResult(0L, 0L, 0L);
        }
    }

    // 보증금 잠금
    public DepositLockResult lockDeposit(Long userId, Long missionId, Long amount) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "missionId", missionId,
                    "amount", amount
            );
            Map<?, ?> response = webClient.post()
                    .uri("/mock/kaia/lock-deposit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return new DepositLockResult(true, 
                        (String) response.get("lockId"), 
                        (String) response.get("txHash"));
            }
            return new DepositLockResult(false, null, null);
        } catch (Exception e) {
            log.error("[StablecoinClient] 보증금 잠금 실패: {}", e.getMessage());
            return new DepositLockResult(false, null, null);
        }
    }

    // 보증금 해제
    public DepositReleaseResult releaseDeposit(String lockId) {
        try {
            Map<?, ?> response = webClient.post()
                    .uri("/mock/kaia/release-deposit/{lockId}", lockId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return new DepositReleaseResult(true, (String) response.get("txHash"));
            }
            return new DepositReleaseResult(false, null);
        } catch (Exception e) {
            log.error("[StablecoinClient] 보증금 해제 실패: {}", e.getMessage());
            return new DepositReleaseResult(false, null);
        }
    }

    // 보증금 출금 (마찰)
    public DepositWithdrawResult withdrawDeposit(String lockId, String reason) {
        try {
            Map<String, Object> request = Map.of("reason", reason);
            Map<?, ?> response = webClient.post()
                    .uri("/mock/kaia/withdraw-deposit/{lockId}", lockId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return new DepositWithdrawResult(true, (String) response.get("txHash"));
            }
            return new DepositWithdrawResult(false, null);
        } catch (Exception e) {
            log.error("[StablecoinClient] 보증금 출금 실패: {}", e.getMessage());
            return new DepositWithdrawResult(false, null);
        }
    }

    // DTO records
    public record TokenMintResult(boolean success, Long mintedAmount, String txHash) {}
    public record TokenBalanceResult(Long balance, Long lockedAmount, Long availableAmount) {}
    public record DepositLockResult(boolean success, String lockId, String txHash) {}
    public record DepositReleaseResult(boolean success, String txHash) {}
    public record DepositWithdrawResult(boolean success, String txHash) {}
}
