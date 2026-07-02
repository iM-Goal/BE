package com.im.agentic.api.buyout;

import com.im.agentic.api.goal.dto.GoalRequestDto.CreateGoalRequest;
import com.im.agentic.api.goal.dto.GoalResponseDto.GoalResponse;
import com.im.agentic.api.goal.dto.GoalResponseDto.BuyOutResponse;
import com.im.agentic.api.support.IntegrationTestBase;
import com.im.agentic.api.support.TestFixtures;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.goal.entity.Goal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

class BuyOutIntegrationTest extends IntegrationTestBase {

    @Autowired
    private GoalRepository goalRepository;

    private String token;
    private Long goalId;

    @BeforeEach
    void setUp() {
        token = signUpAndLogin();
        HttpEntity<CreateGoalRequest> entity = new HttpEntity<>(
                TestFixtures.createGoalRequest(), authHeaders(token));
        ResponseEntity<String> goalResponse = restTemplate.postForEntity(
                baseUrl() + "/api/goals", entity, String.class);
        GoalResponse goalBody = getResponseData(goalResponse, GoalResponse.class);
        goalId = goalBody.getId();

        // lockId 주입
        Goal goal = goalRepository.findById(goalId).orElseThrow();
        goal.setLockId("test-lock-id");
        goalRepository.save(goal);
    }

    @Test
    @DisplayName("Buy-out 성공 - Kaia Unlock → iMKRW Burn → DAGAON 출금 순서 검증")
    void buyOut_success_callsUnlockBurnWithdrawInOrder() {
        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/goals/" + goalId + "/buyout",
                new HttpEntity<>(authHeaders(token)), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BuyOutResponse body = getResponseData(response, BuyOutResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).contains("DAGAON");

        // Kaia Unlock 호출 검증
        fastapiMock.verify(1, postRequestedFor(urlMatching("/mock/kaia/unlock/.*")));
        // iMKRW Burn 호출 검증
        fastapiMock.verify(1, postRequestedFor(urlEqualTo("/mock/imkrw/burn")));
        // DAGAON 출금 호출 검증
        fastapiMock.verify(1, postRequestedFor(urlEqualTo("/mock/dagaon/withdraw")));
    }

    @Test
    @DisplayName("타인의 목표 Buy-out 시도 → 401 반환")
    void buyOut_otherUsersGoal_returns401() {
        // given — 다른 유저 생성
        restTemplate.postForEntity(baseUrl() + "/api/auth/signup",
                new com.im.agentic.api.user.dto.UserRequestDto.SignUpRequest() {{
                    setEmail("other@im.com"); setPassword("test1234!");
                }}, String.class);
        ResponseEntity<String> loginResp =
                restTemplate.postForEntity(baseUrl() + "/api/auth/login",
                        new com.im.agentic.api.user.dto.UserRequestDto.LoginRequest() {{
                            setEmail("other@im.com"); setPassword("test1234!");
                        }}, String.class);
        
        String otherToken;
        try {
            otherToken = objectMapper.readTree(loginResp.getBody()).path("data").path("token").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // when — 다른 유저가 내 목표를 Buy-out 시도
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/goals/" + goalId + "/buyout",
                new HttpEntity<>(authHeaders(otherToken)), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

