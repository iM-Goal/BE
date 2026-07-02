package com.im.agentic.api.mission.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.deposit.dto.DepositResponseDto;
import com.im.agentic.api.mission.dto.MissionResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.deposit.entity.Deposit;
import com.im.agentic.domain.deposit.entity.DepositStatus;
import com.im.agentic.domain.deposit.repository.DepositRepository;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalStatus;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.mission.entity.MissionSource;
import com.im.agentic.domain.mission.entity.MissionStatus;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.mission.repository.MissionRepository;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentClient;
import com.im.agentic.infra.blockchain.StablecoinClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MissionService {

    private final MissionRepository missionRepository;
    private final DepositRepository depositRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final MyDataCacheRepository myDataCacheRepository;
    private final AgentClient agentClient;
    private final StablecoinClient stablecoinClient;
    private final ObjectMapper objectMapper;

    // 오늘 미션 조회 (IN_PROGRESS)
    public List<MissionResponseDto> getTodayMissions(Long userId) {
        return missionRepository.findByUserIdAndStatus(userId, MissionStatus.IN_PROGRESS)
                .stream()
                .map(MissionResponseDto::from)
                .collect(Collectors.toList());
    }

    // 제안된 미션 조회 (PROPOSED)
    public List<MissionResponseDto> getProposedMissions(Long userId) {
        return missionRepository.findByUserIdAndStatus(userId, MissionStatus.PROPOSED)
                .stream()
                .map(MissionResponseDto::from)
                .collect(Collectors.toList());
    }

    // 미션 상세 조회
    public MissionResponseDto getMission(Long userId, Long missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new AgenticException(ErrorCode.MISSION_NOT_FOUND));
        if (!mission.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.MISSION_NOT_OWNED);
        }
        return MissionResponseDto.from(mission);
    }

    // 미션 수락 -> 보증금 잠금
    @Transactional
    public DepositResponseDto acceptMission(Long userId, Long missionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new AgenticException(ErrorCode.MISSION_NOT_FOUND));

        if (!mission.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.MISSION_NOT_OWNED);
        }
        if (mission.getStatus() != MissionStatus.PROPOSED) {
            throw new AgenticException(ErrorCode.MISSION_NOT_PROPOSED);
        }

        // 1. 미션 상태 변경
        mission.accept();

        // 2. iMKRW 발행
        StablecoinClient.TokenMintResult mintResult = stablecoinClient.mintToken(userId, mission.getDepositAmount());
        if (!mintResult.success()) {
            throw new AgenticException(ErrorCode.INVALID_INPUT);
        }

        // 3. Kaia 스마트컨트랙트 잠금
        StablecoinClient.DepositLockResult lockResult = stablecoinClient.lockDeposit(userId, missionId, mission.getDepositAmount());
        if (!lockResult.success()) {
            throw new AgenticException(ErrorCode.INVALID_INPUT);
        }

        // 4. Deposit 생성 및 저장
        Deposit deposit = Deposit.builder()
                .mission(mission)
                .user(user)
                .amount(mission.getDepositAmount())
                .imkrwAmount(mintResult.mintedAmount())
                .status(DepositStatus.LOCKED)
                .lockTxHash(lockResult.txHash())
                .lockedAt(LocalDateTime.now())
                .build();

        Deposit savedDeposit = depositRepository.save(deposit);
        return DepositResponseDto.from(savedDeposit);
    }

    // 직접 미션 초과 감지 및 제안 생성
    @Transactional
    public void detectDirectOverageAndPropose(Long userId, Long goalId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        if (goal.getDailyBudget() == null || goal.getDailyBudget() <= 0) {
            return;
        }

        // 오늘 자정부터 현재까지 총 소비 계산
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todaySpend = transactionRepository.sumTodaySpend(userId, startOfDay);
        long overageAmount = todaySpend - goal.getDailyBudget();

        if (overageAmount <= 0) {
            return;
        }

        MyDataCache cache = myDataCacheRepository.findByUserId(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        Map<String, Long> categoryExpenses = new HashMap<>();
        try {
            categoryExpenses = objectMapper.readValue(cache.getCategoryExpenses(),
                    new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.error("[MissionService] categoryExpenses 파싱 실패: {}", e.getMessage());
        }

        // FastAPI Agent 호출
        AgentClient.OverageDetectResponse agentResponse = agentClient.detectOverage(
                userId, goal.getDailyBudget(), todaySpend, categoryExpenses);

        if (!agentResponse.isHasOverage() || agentResponse.getMissions() == null) {
            return;
        }

        // 미션 생성 및 제안
        for (AgentClient.MissionProposalDto prop : agentResponse.getMissions()) {
            Mission mission = Mission.builder()
                    .user(user)
                    .goal(goal)
                    .missionSource(MissionSource.DIRECT)
                    .status(MissionStatus.PROPOSED)
                    .category(mapToCategory(prop.getCategory()))
                    .description(prop.getDescription())
                    .limitAmount(prop.getLimitAmount())
                    .depositAmount(prop.getDepositAmount())
                    .durationDays(prop.getDurationDays())
                    .overageRate(agentResponse.getOverageRate())
                    .proposalReason("오늘 가용 잔액을 초과했습니다.")
                    .build();
            missionRepository.save(mission);
        }
    }

    // 자동 미션 과소비 감지 및 제안 생성
    @Transactional
    public void detectAutoOverageAndPropose(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));
        MyDataCache cache = myDataCacheRepository.findByUserId(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // 이번 주 월요일 00:00부터 현재까지의 카테고리별 지출 집계
        LocalDateTime startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        List<Transaction> thisWeekTxs = transactionRepository
                .findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(userId, startOfWeek, LocalDateTime.now());

        Map<String, Long> weeklyExpenses = new HashMap<>();
        for (Transaction tx : thisWeekTxs) {
            if (tx.getCategory() != null) {
                String catName = mapToKorean(tx.getCategory());
                weeklyExpenses.put(catName, weeklyExpenses.getOrDefault(catName, 0L) + tx.getAmount());
            }
        }

        Map<String, Long> weeklyAvgExpenses = new HashMap<>();
        try {
            weeklyAvgExpenses = objectMapper.readValue(cache.getWeeklyAvgExpenses(),
                    new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.error("[MissionService] weeklyAvgExpenses 파싱 실패: {}", e.getMessage());
        }

        // FastAPI Agent 호출
        AgentClient.AutoOverageResponse agentResponse = agentClient.detectAutoOverage(
                userId, weeklyExpenses, weeklyAvgExpenses);

        if (!agentResponse.isHasOverage() || agentResponse.getOverageCategories() == null) {
            return;
        }

        for (AgentClient.OverageCategoryDto oc : agentResponse.getOverageCategories()) {
            Mission mission = Mission.builder()
                    .user(user)
                    .goal(null)
                    .missionSource(MissionSource.AUTO)
                    .status(MissionStatus.PROPOSED)
                    .category(mapToCategory(oc.getCategory()))
                    .description(agentResponse.getProposalReason())
                    .limitAmount(oc.getAvgWeek()) // limit = 평균 지출 수준
                    .depositAmount(oc.getOverageAmount()) // deposit = 초과분 보증금 설정
                    .durationDays(7)
                    .overageRate(oc.getOverageRate())
                    .proposalReason(agentResponse.getProposalReason())
                    .build();
            missionRepository.save(mission);
        }
    }

    // 미션 판단 (스케줄러에서 호출)
    @Transactional
    public void judgeMission(Long missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new AgenticException(ErrorCode.MISSION_NOT_FOUND));

        if (mission.getStatus() != MissionStatus.IN_PROGRESS) {
            return;
        }

        // 미션 기간(startDate ~ endDate) 동안 해당 카테고리 총 소비 계산
        LocalDateTime start = mission.getStartDate().atStartOfDay();
        LocalDateTime end = mission.getEndDate().atTime(LocalTime.MAX);
        List<Transaction> txs = transactionRepository
                .findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(mission.getUser().getId(), start, end);

        long totalSpend = txs.stream()
                .filter(t -> t.getCategory() == mission.getCategory())
                .mapToLong(Transaction::getAmount)
                .sum();

        // FastAPI Agent 호출
        AgentClient.ExecutionPeriodResponse agentResponse = agentClient.executeMissionPeriod(
                mission.getUser().getId(),
                missionId,
                mission.getCategory() != null ? mission.getCategory().name() : null,
                mission.getLimitAmount(),
                mission.getDurationDays(),
                totalSpend
        );

        if (agentResponse.isDepositReturn()) {
            // 미션 성공
            mission.succeed();
            Deposit deposit = depositRepository.findByMissionId(missionId)
                    .orElseThrow(() -> new AgenticException(ErrorCode.DEPOSIT_NOT_FOUND));

            // 보증금 잠금 해제 (스마트컨트랙트)
            StablecoinClient.DepositReleaseResult releaseResult = stablecoinClient.releaseDeposit(deposit.getLockTxHash());
            String txHash = releaseResult.success() ? releaseResult.txHash() : "0xrelease_fallback";

            deposit.release(txHash);
        } else {
            // 미션 실패 -> 보증금은 잠금 상태 유지 (LOCKED)
            mission.fail();
        }
    }

    private SpendingCategory mapToCategory(String koreanName) {
        if (koreanName == null) return null;
        return switch (koreanName) {
            case "카페" -> SpendingCategory.CAFE;
            case "배달음식", "배달" -> SpendingCategory.DELIVERY;
            case "편의점" -> SpendingCategory.CONVENIENCE;
            case "식비" -> SpendingCategory.FOOD;
            case "교통" -> SpendingCategory.TRANSPORT;
            case "쇼핑" -> SpendingCategory.SHOPPING;
            default -> {
                try {
                    yield SpendingCategory.valueOf(koreanName.toUpperCase());
                } catch (Exception e) {
                    yield SpendingCategory.FOOD;
                }
            }
        };
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
