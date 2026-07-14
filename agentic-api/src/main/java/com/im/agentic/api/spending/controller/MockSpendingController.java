package com.im.agentic.api.spending.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.spending.dto.TriggerSpendingRequestDto;
import com.im.agentic.api.spending.dto.TriggerSpendingResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mock", description = "시연 및 데모용 Mock API")
public class MockSpendingController {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MyDataCacheRepository myDataCacheRepository;
    private final AgentClient agentClient;
    private final ObjectMapper objectMapper;

    @PostMapping("/trigger-spending")
    @Operation(summary = "가짜 결제 발생 및 실시간 알림 반환", description = "결제를 트리거하고 AI 과소비 감지 결과를 즉시 반환합니다.")
    public ResponseEntity<TriggerSpendingResponseDto> triggerMockSpending(@RequestBody TriggerSpendingRequestDto request) {
        log.info("[Mock API] 가짜 결제 트리거 요청: {}", request);

        // 1. 유저 검증
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        MyDataCache cache = myDataCacheRepository.findByUserId(request.userId())
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // 2. 가짜 트랜잭션 저장
        SpendingCategory category;
        try {
            category = SpendingCategory.valueOf(request.category().toUpperCase());
        } catch (IllegalArgumentException e) {
            category = SpendingCategory.SHOPPING; // 기본값
        }

        Transaction transaction = Transaction.builder()
                .user(user)
                .merchant(request.merchantName())
                .amount(request.amount())
                .category(category)
                .transactedAt(LocalDateTime.now())
                .build();
        
        transactionRepository.save(transaction);

        // 3. 이번 주 카테고리별 누적 지출 계산
        LocalDateTime startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime endOfWeek = startOfWeek.plusDays(7).minusNanos(1);
        List<Transaction> thisWeekTxs = transactionRepository
                .findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(request.userId(), startOfWeek, endOfWeek);

        Map<String, Long> weeklyByCategory = new HashMap<>();
        for (Transaction tx : thisWeekTxs) {
            if (tx.getCategory() != null) {
                String korName = mapToKorean(tx.getCategory());
                weeklyByCategory.put(korName, weeklyByCategory.getOrDefault(korName, 0L) + tx.getAmount());
            }
        }

        // 4. 유저 4주 평균 지출 파싱
        Map<String, Long> myWeeklyAvg = new HashMap<>();
        try {
            myWeeklyAvg = objectMapper.readValue(cache.getWeeklyAvgExpenses(),
                    new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.warn("[Mock API] weeklyAvgExpenses 파싱 실패: {}", e.getMessage());
        }

        // 5. AI 자동 과소비 감지 호출
        AgentClient.AutoOverageResponse overageResponse = agentClient.detectAutoOverage(
                request.userId(),
                weeklyByCategory,
                myWeeklyAvg,
                user.getIncomeQuintile()
        );

        // 6. 응답 생성
        TriggerSpendingResponseDto responseDto = new TriggerSpendingResponseDto(
                true,
                overageResponse.isHasOverage(),
                overageResponse.isHasOverage() ? overageResponse.getProposalReason() : null
        );

        return ResponseEntity.ok(responseDto);
    }

    private String mapToKorean(SpendingCategory category) {
        if (category == null) return "기타";
        return switch (category) {
            case CAFE -> "카페";
            case DELIVERY -> "배달음식";
            case CONVENIENCE -> "편의점";
            case FOOD -> "식비";
            case TRANSPORT -> "교통";
            case SHOPPING -> "쇼핑";
        };
    }
}
