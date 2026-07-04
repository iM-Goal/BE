package com.im.agentic.api.goal.service;

import com.im.agentic.api.goal.dto.GoalRequestDto.CreateGoalRequest;
import com.im.agentic.api.goal.dto.GoalRequestDto.UpdateGoalRequest;
import com.im.agentic.api.goal.dto.GoalResponseDto.GoalListResponse;
import com.im.agentic.api.goal.dto.GoalResponseDto.GoalResponse;
import com.im.agentic.api.goal.dto.GoalResponseDto.ProgressResponse;
import com.im.agentic.api.goal.dto.GoalResponseDto.BuyOutResponse;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalSlotType;
import com.im.agentic.domain.goal.entity.GoalStatus;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentOrchestrator;
import com.im.agentic.infra.agent.dto.GoalParsingOutput;
import com.im.agentic.api.programmable.service.ProgrammableMoneyService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final ProgrammableMoneyService programmableMoneyService;



    @Transactional
    public GoalResponse createGoal(Long userId, CreateGoalRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // Check if an ACTIVE goal already exists in the same slot
        goalRepository.findByUserIdAndSlotTypeAndStatus(userId, request.getSlotType(), GoalStatus.ACTIVE)
                .ifPresent(existingGoal -> {
                    throw new AgenticException(ErrorCode.SLOT_ALREADY_OCCUPIED);
                });

        // Calculate estimated completion date based on slot type and duration days
        LocalDate estimatedDate = calculateCompletionDate(request.getSlotType(), request.getDurationDays());

        String finalItemName = request.getItemName();
        Long finalTargetAmount = request.getTargetAmount();

        // 1. If rawInput is provided, analyze target item and price via GoalParsingAgent
        if (request.getRawInput() != null && !request.getRawInput().isBlank()) {
            GoalParsingOutput parsed = agentOrchestrator.parseGoal(
                    userId, 
                    request.getRawInput(), 
                    user.getIncomeQuintile(), 
                    user.getDailyAvailable()
            );
            if (parsed.getItemName() == null || parsed.getItemName().isBlank() || parsed.getPrice() == null || parsed.getPrice() <= 0) {
                throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
            }
            finalItemName = parsed.getItemName();
            finalTargetAmount = parsed.getPrice();
        }

        if (finalItemName == null || finalItemName.isBlank() || finalTargetAmount == null || finalTargetAmount <= 0) {
            throw new AgenticException(ErrorCode.INVALID_INPUT);
        }

        Goal goal = Goal.builder()
                .user(user)
                .itemName(finalItemName)
                .targetAmount(finalTargetAmount)
                .slotType(request.getSlotType())
                .status(GoalStatus.ACTIVE)
                .registrationType(request.getRegistrationType())
                .itemImageUrl(request.getItemImageUrl())
                .estimatedCompletionDate(estimatedDate)
                .dailyBudget(calculateDailyBudget(user, request.getDurationDays()))
                .durationDays(request.getDurationDays())
                .build();

        Goal savedGoal = goalRepository.save(goal);
        return GoalResponse.from(savedGoal);

    }

    public GoalListResponse getGoals(Long userId) {
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, GoalStatus.ACTIVE);

        GoalResponse shortGoal = null;
        GoalResponse midGoal = null;
        GoalResponse longGoal = null;

        for (Goal goal : activeGoals) {
            if (goal.getSlotType() == GoalSlotType.SHORT) {
                shortGoal = GoalResponse.from(goal);
            } else if (goal.getSlotType() == GoalSlotType.MID) {
                midGoal = GoalResponse.from(goal);
            } else if (goal.getSlotType() == GoalSlotType.LONG) {
                longGoal = GoalResponse.from(goal);
            }
        }

        return GoalListResponse.builder()
                .shortGoal(shortGoal)
                .midGoal(midGoal)
                .longGoal(longGoal)
                .build();
    }

    public GoalResponse getGoal(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        if (!goal.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.UNAUTHORIZED);
        }

        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse updateGoal(Long userId, Long goalId, UpdateGoalRequest request) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        if (!goal.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.UNAUTHORIZED);
        }

        goal.updateGoalDetails(request.getItemName(), request.getTargetAmount(), request.getItemImageUrl());

        return GoalResponse.from(goal);
    }

    @Transactional
    public void deleteGoal(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        if (!goal.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.UNAUTHORIZED);
        }

        goal.cancel();
    }

    public ProgressResponse getGoalProgress(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        if (!goal.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.UNAUTHORIZED);
        }

        return ProgressResponse.from(goal);
    }

    private LocalDate calculateCompletionDate(GoalSlotType slotType, Integer durationDays) {
        if (durationDays != null && durationDays > 0) {
            return LocalDate.now().plusDays(durationDays);
        }
        LocalDate today = LocalDate.now();
        return switch (slotType) {
            case SHORT -> today.plusDays(30);
            case MID -> today.plusDays(90);
            case LONG -> today.plusDays(365);
        };
    }

    private Long calculateDailyBudget(User user, Integer durationDays) {
        if (user.getMonthlyIncome() == null || user.getMonthlyIncome() <= 0) return null;
        long income = user.getMonthlyIncome();
        long fixed = user.getFixedExpense() != null ? user.getFixedExpense() : 0L;
        int days = (durationDays != null && durationDays > 0) ? durationDays : 30;
        return (income - fixed) / days;
    }

    @Transactional
    public BuyOutResponse buyOut(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AgenticException(ErrorCode.GOAL_NOT_FOUND));

        if (!goal.getUser().getId().equals(userId)) {
            throw new AgenticException(ErrorCode.UNAUTHORIZED);
        }

        // iMKRW 소각 + DAGAON 출금 + 스마트컨트랙트 해제 통합 처리
        ProgrammableMoneyService.BuyOutTokenResult tokenResult =
                programmableMoneyService.buyOutWithToken(userId, goalId);

        goal.complete();

        return BuyOutResponse.builder()
                .goalId(goalId)
                .itemName(goal.getItemName())
                .totalSavedAmount(goal.getCurrentAmount())
                .message(tokenResult.message())
                .build();
    }
}

