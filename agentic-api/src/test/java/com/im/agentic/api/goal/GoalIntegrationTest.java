package com.im.agentic.api.goal;

import com.im.agentic.api.goal.dto.GoalRequestDto.CreateGoalRequest;
import com.im.agentic.api.goal.dto.GoalResponseDto.GoalResponse;
import com.im.agentic.api.support.IntegrationTestBase;
import com.im.agentic.api.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class GoalIntegrationTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void setUp() {
        token = signUpAndLogin();
    }

    @Test
    @DisplayName("목표 등록 성공 - GoalParsingAgent 호출 후 itemName 파싱 반환")
    void createGoal_withRawInput_callsGoalParsingAgent() {
        // given
        CreateGoalRequest request = TestFixtures.createGoalRequest();
        HttpEntity<CreateGoalRequest> entity = new HttpEntity<>(request, authHeaders(token));

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/goals", entity, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        GoalResponse body = getResponseData(response, GoalResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.getItemName()).isEqualTo("나이키 에어맥스");
        assertThat(body.getTargetAmount()).isEqualTo(128000L);

        // GoalParsingAgent가 실제로 호출됐는지 검증
        goalParsingMock.verify(1,
                com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/agent/parse-goal")));
    }

    @Test
    @DisplayName("목표 등록 성공 - rawInput 없이 수동 등록")
    void createGoal_withoutRawInput_skipsGoalParsingAgent() {
        // given
        CreateGoalRequest request = TestFixtures.createGoalRequestWithoutRawInput();
        HttpEntity<CreateGoalRequest> entity = new HttpEntity<>(request, authHeaders(token));

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/goals", entity, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        GoalResponse body = getResponseData(response, GoalResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.getItemName()).isEqualTo("아이패드 프로");

        // GoalParsingAgent가 호출되지 않아야 함
        goalParsingMock.verify(0,
                com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/agent/parse-goal")));
    }

    @Test
    @DisplayName("같은 슬롯 중복 등록 → 409 반환")
    void createGoal_withDuplicateSlot_returns409() {
        // given
        HttpEntity<CreateGoalRequest> entity = new HttpEntity<>(
                TestFixtures.createGoalRequest(), authHeaders(token));
        restTemplate.postForEntity(baseUrl() + "/api/goals", entity, String.class);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/goals", entity, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("목표 목록 조회 - 등록한 목표 반환")
    void getGoals_afterCreation_returnsGoalList() {
        // given
        HttpEntity<CreateGoalRequest> entity = new HttpEntity<>(
                TestFixtures.createGoalRequest(), authHeaders(token));
        restTemplate.postForEntity(baseUrl() + "/api/goals", entity, String.class);

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/goals", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}

