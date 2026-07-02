package com.im.agentic.api.mission;

import com.im.agentic.api.deposit.dto.DepositResponseDto;
import com.im.agentic.api.mission.dto.MissionResponseDto;
import com.im.agentic.api.mission.service.MissionService;
import com.im.agentic.api.support.IntegrationTestBase;
import com.im.agentic.domain.deposit.entity.Deposit;
import com.im.agentic.domain.deposit.entity.DepositStatus;
import com.im.agentic.domain.deposit.repository.DepositRepository;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalSlotType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

class MissionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MissionService missionService;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MyDataCacheRepository myDataCacheRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DepositRepository depositRepository;

    private User testUser;
    private Goal testGoal;

    @BeforeEach
    void setUp() {
        depositRepository.deleteAllInBatch();
        missionRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        goalRepository.deleteAllInBatch();
        myDataCacheRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = User.builder()
                .email("test@im.com")
                .password("test1234!")
                .nickname("테스트")
                .monthlyIncome(3000000L)
                .fixedExpense(1000000L)
                .build();
        testUser = userRepository.save(testUser);

        testGoal = Goal.builder()
                .user(testUser)
                .itemName("아이패드")
                .targetAmount(1000000L)
                .slotType(GoalSlotType.SHORT)
                .status(GoalStatus.ACTIVE)
                .registrationType("MANUAL")
                .dailyBudget(50000L)
                .durationDays(30)
                .build();
        testGoal = goalRepository.save(testGoal);

        MyDataCache cache = MyDataCache.builder()
                .user(testUser)
                .categoryExpenses("{\"식비\": 300000}")
                .weeklyAvgExpenses("{\"식비\": 70000}")
                .dailyAvailableAmount(50000L)
                .lastUpdatedAt(LocalDateTime.now())
                .build();
        myDataCacheRepository.save(cache);
    }

    @Test
    @DisplayName("직접 미션 초과 감지 -> PROPOSED 미션 제안 생성")
    void detectDirectOverage_proposesMissions() {
        // given (오늘 소비 70,000원 -> 예산 50,000원 초과)
        Transaction tx = Transaction.builder()
                .user(testUser)
                .amount(70000L)
                .merchant("맥도날드")
                .category(SpendingCategory.FOOD)
                .transactedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);

        // AI Agent mock 설정
        missionMock.stubFor(post(urlEqualTo("/agent/detect-overage"))
                .willReturn(okJson("{\n" +
                        "  \"hasOverage\": true,\n" +
                        "  \"overageAmount\": 20000,\n" +
                        "  \"overageRate\": 40.0,\n" +
                        "  \"missionCount\": 1,\n" +
                        "  \"missions\": [\n" +
                        "    {\n" +
                        "      \"category\": \"FOOD\",\n" +
                        "      \"description\": \"식비 줄이기\",\n" +
                        "      \"limitAmount\": 15000,\n" +
                        "      \"depositAmount\": 20000,\n" +
                        "      \"durationDays\": 3\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}")));

        // when
        missionService.detectDirectOverageAndPropose(testUser.getId(), testGoal.getId());

        // then
        List<Mission> proposed = missionRepository.findByUserIdAndStatus(testUser.getId(), MissionStatus.PROPOSED);
        assertThat(proposed).hasSize(1);
        assertThat(proposed.get(0).getCategory()).isEqualTo(SpendingCategory.FOOD);
        assertThat(proposed.get(0).getDepositAmount()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("미션 수락 -> LOCKED 보증금 생성")
    void acceptMission_locksDeposit() {
        // given
        Mission proposedMission = Mission.builder()
                .user(testUser)
                .goal(testGoal)
                .missionSource(MissionSource.DIRECT)
                .status(MissionStatus.PROPOSED)
                .category(SpendingCategory.CAFE)
                .depositAmount(10000L)
                .durationDays(3)
                .build();
        proposedMission = missionRepository.save(proposedMission);

        // Blockchain Mock 설정
        fastapiMock.stubFor(post(urlEqualTo("/mock/imkrw/mint"))
                .willReturn(okJson("{\"success\": true, \"mintedAmount\": 10000, \"txHash\": \"0xmint\"}")));
        fastapiMock.stubFor(post(urlEqualTo("/mock/kaia/lock-deposit"))
                .willReturn(okJson("{\"success\": true, \"lockId\": \"lock123\", \"txHash\": \"0xlock\"}")));

        // when
        DepositResponseDto response = missionService.acceptMission(testUser.getId(), proposedMission.getId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DepositStatus.LOCKED);
        assertThat(response.getAmount()).isEqualTo(10000L);
        assertThat(response.getLockTxHash()).isEqualTo("0xlock");

        Mission accepted = missionRepository.findById(proposedMission.getId()).orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("미션 판단 -> 성공 시 보증금 RELEASED")
    void judgeMission_success_releasesDeposit() {
        // given
        Mission inProgress = Mission.builder()
                .user(testUser)
                .goal(testGoal)
                .missionSource(MissionSource.DIRECT)
                .status(MissionStatus.IN_PROGRESS)
                .category(SpendingCategory.CAFE)
                .limitAmount(10000L)
                .depositAmount(10000L)
                .durationDays(1)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now())
                .build();
        inProgress = missionRepository.save(inProgress);

        Deposit deposit = Deposit.builder()
                .mission(inProgress)
                .user(testUser)
                .amount(10000L)
                .imkrwAmount(10000L)
                .status(DepositStatus.LOCKED)
                .lockTxHash("0xlock")
                .build();
        depositRepository.save(deposit);

        // 카페 지출 5,000원 등록 (한도 10,000원 지킴)
        Transaction tx = Transaction.builder()
                .user(testUser)
                .amount(5000L)
                .category(SpendingCategory.CAFE)
                .transactedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);

        // Agent Mock 설정
        executionMock.stubFor(post(urlEqualTo("/agent/execute"))
                .willReturn(okJson("{\"success\": true, \"depositReturn\": true, \"message\": \"성공\"}")));
        fastapiMock.stubFor(post(urlEqualTo("/mock/kaia/release-deposit/0xlock"))
                .willReturn(okJson("{\"success\": true, \"lockId\": \"0xlock\", \"txHash\": \"0xrelease\", \"status\": \"RELEASED\"}")));

        // when
        missionService.judgeMission(inProgress.getId());

        // then
        Mission judged = missionRepository.findById(inProgress.getId()).orElseThrow();
        assertThat(judged.getStatus()).isEqualTo(MissionStatus.SUCCESS);

        Deposit resultDeposit = depositRepository.findByMissionId(inProgress.getId()).orElseThrow();
        assertThat(resultDeposit.getStatus()).isEqualTo(DepositStatus.RELEASED);
        assertThat(resultDeposit.getReleaseTxHash()).isEqualTo("0xrelease");
    }
}
