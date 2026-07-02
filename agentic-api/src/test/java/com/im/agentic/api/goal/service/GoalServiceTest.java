package com.im.agentic.api.goal.service;

import com.im.agentic.api.goal.dto.GoalRequestDto.CreateGoalRequest;
import com.im.agentic.api.goal.dto.GoalResponseDto.GoalResponse;
import com.im.agentic.api.programmable.service.ProgrammableMoneyService;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @InjectMocks
    private GoalService goalService;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AgentOrchestrator agentOrchestrator;

    @Mock
    private ProgrammableMoneyService programmableMoneyService;

    @Test
    @DisplayName("목표 등록 성공 - 자연어 파싱 포함")
    void createGoal_success_withRawInput() {
        User user = User.builder().id(1L).email("test@im.com").password("encoded").build();

        CreateGoalRequest request = new CreateGoalRequest();
        request.setItemName("나이키 에어맥스");
        request.setTargetAmount(128000L);
        request.setSlotType(GoalSlotType.SHORT);
        request.setRegistrationType("NATURAL_LANGUAGE");
        request.setRawInput("나이키 에어맥스 사고싶어");

        GoalParsingOutput parsingOutput = GoalParsingOutput.builder()
                .itemName("나이키 에어맥스")
                .price(128000L)
                .category("패션")
                .build();

        given(userRepository.findById(anyLong())).willReturn(Optional.of(user));
        given(goalRepository.findByUserIdAndSlotTypeAndStatus(any(), any(), any()))
                .willReturn(Optional.empty());
        given(agentOrchestrator.parseGoal(anyLong(), anyString())).willReturn(parsingOutput);
        given(goalRepository.save(any(Goal.class))).willAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.createGoal(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getItemName()).isEqualTo("나이키 에어맥스");
        assertThat(response.getTargetAmount()).isEqualTo(128000L);
    }

    @Test
    @DisplayName("슬롯 중복 시 SLOT_ALREADY_OCCUPIED 예외")
    void createGoal_slotAlreadyOccupied() {
        User user = User.builder().id(1L).email("test@im.com").password("encoded").build();
        Goal existingGoal = Goal.builder()
                .id(1L)
                .user(user)
                .slotType(GoalSlotType.SHORT)
                .status(GoalStatus.ACTIVE)
                .itemName("기존 목표")
                .targetAmount(100000L)
                .registrationType("NATURAL_LANGUAGE")
                .estimatedCompletionDate(LocalDate.now().plusDays(30))
                .build();

        CreateGoalRequest request = new CreateGoalRequest();
        request.setItemName("새 목표");
        request.setTargetAmount(200000L);
        request.setSlotType(GoalSlotType.SHORT);
        request.setRegistrationType("NATURAL_LANGUAGE");
        request.setRawInput("새 목표");

        given(userRepository.findById(anyLong())).willReturn(Optional.of(user));
        given(goalRepository.findByUserIdAndSlotTypeAndStatus(any(), any(), any()))
                .willReturn(Optional.of(existingGoal));

        assertThatThrownBy(() -> goalService.createGoal(1L, request))
                .isInstanceOf(AgenticException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SLOT_ALREADY_OCCUPIED);
    }

    @Test
    @DisplayName("목표 삭제 - CANCELLED 상태 변경")
    void deleteGoal_cancelled() {
        User user = User.builder().id(1L).email("test@im.com").password("encoded").build();
        Goal goal = Goal.builder()
                .id(1L)
                .user(user)
                .slotType(GoalSlotType.SHORT)
                .status(GoalStatus.ACTIVE)
                .itemName("나이키 에어맥스")
                .targetAmount(128000L)
                .registrationType("NATURAL_LANGUAGE")
                .estimatedCompletionDate(LocalDate.now().plusDays(30))
                .build();

        given(goalRepository.findById(anyLong())).willReturn(Optional.of(goal));

        goalService.deleteGoal(1L, 1L);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.CANCELLED);
    }
}
