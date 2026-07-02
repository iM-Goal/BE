package com.im.agentic.api.mission.service;

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
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentClient;
import com.im.agentic.infra.blockchain.StablecoinClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @InjectMocks
    private MissionService missionService;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private StablecoinClient stablecoinClient;

    private User mockUser() {
        return User.builder().id(1L).email("test@im.com").password("encoded").build();
    }

    @Test
    @DisplayName("오늘 진행 중인 미션 조회")
    void getTodayMissions_success() {
        User user = mockUser();
        Mission mission = Mission.builder()
                .id(1L)
                .user(user)
                .missionSource(MissionSource.DIRECT)
                .status(MissionStatus.IN_PROGRESS)
                .category(SpendingCategory.FOOD)
                .depositAmount(20000L)
                .build();

        given(missionRepository.findByUserIdAndStatus(anyLong(), any())).willReturn(List.of(mission));

        List<MissionResponseDto> response = missionService.getTodayMissions(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getCategory()).isEqualTo(SpendingCategory.FOOD);
    }

    @Test
    @DisplayName("미션 수락 - 성공")
    void acceptMission_success() {
        User user = mockUser();
        Mission proposed = Mission.builder()
                .id(1L)
                .user(user)
                .missionSource(MissionSource.DIRECT)
                .status(MissionStatus.PROPOSED)
                .category(SpendingCategory.FOOD)
                .depositAmount(10000L)
                .build();

        given(userRepository.findById(anyLong())).willReturn(Optional.of(user));
        given(missionRepository.findById(anyLong())).willReturn(Optional.of(proposed));
        given(stablecoinClient.mintToken(anyLong(), anyLong()))
                .willReturn(new StablecoinClient.TokenMintResult(true, 10000L, "0xmint"));
        given(stablecoinClient.lockDeposit(anyLong(), anyLong(), anyLong()))
                .willReturn(new StablecoinClient.DepositLockResult(true, "lock123", "0xlock"));
        given(depositRepository.save(any(Deposit.class))).willAnswer(inv -> inv.getArgument(0));

        DepositResponseDto response = missionService.acceptMission(1L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DepositStatus.LOCKED);
        assertThat(proposed.getStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
    }
}
