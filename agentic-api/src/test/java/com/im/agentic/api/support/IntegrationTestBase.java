package com.im.agentic.api.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.mission.repository.MissionRepository;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.embedding.repository.MissionCaseRepository;
import com.im.agentic.domain.embedding.repository.SpendingEmbeddingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    // 레포지토리 주입 (데이터 클린업용)
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected GoalRepository goalRepository;
    @Autowired
    protected MissionRepository missionRepository;
    @Autowired
    protected TransactionRepository transactionRepository;
    @Autowired
    protected MyDataCacheRepository myDataCacheRepository;
    @Autowired
    protected MissionCaseRepository missionCaseRepository;
    @Autowired
    protected SpendingEmbeddingRepository spendingEmbeddingRepository;

    // WireMock 서버 4개 (에이전트별 독립 포트) + FastAPI 1개
    protected static WireMockServer goalParsingMock = new WireMockServer(WireMockConfiguration.options().port(18001));
    protected static WireMockServer spendingMock = new WireMockServer(WireMockConfiguration.options().port(18002));
    protected static WireMockServer missionMock = new WireMockServer(WireMockConfiguration.options().port(18003));
    protected static WireMockServer executionMock = new WireMockServer(WireMockConfiguration.options().port(18004));
    protected static WireMockServer fastapiMock = new WireMockServer(WireMockConfiguration.options().port(18000));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("agent.goal-parsing.url", () -> "http://localhost:18001");
        registry.add("agent.spending.url", () -> "http://localhost:18002");
        registry.add("agent.mission.url", () -> "http://localhost:18003");
        registry.add("agent.execution.url", () -> "http://localhost:18004");
        registry.add("fastapi.base-url", () -> "http://localhost:18000");
    }

    @BeforeEach
    void setUpMocksAndDb() {
        // DB 격리 클린업
        cleanupDatabase();

        goalParsingMock.start();
        spendingMock.start();
        missionMock.start();
        executionMock.start();
        fastapiMock.start();

        // WireMock 요청 리셋
        goalParsingMock.resetRequests();
        spendingMock.resetRequests();
        missionMock.resetRequests();
        executionMock.resetRequests();
        fastapiMock.resetRequests();

        // 기본 스텁 등록
        WireMockSupport.stubGoalParsingAgent(goalParsingMock);
        WireMockSupport.stubSpendingAnalysisAgent(spendingMock);
        WireMockSupport.stubMissionGenerationAgent(missionMock);
        WireMockSupport.stubExecutionAgent(executionMock);
        WireMockSupport.stubSpendingHistory(fastapiMock);
        WireMockSupport.stubMintToken(fastapiMock, 5000L);
        WireMockSupport.stubLockToken(fastapiMock);
        WireMockSupport.stubUnlockToken(fastapiMock);
        WireMockSupport.stubBurnToken(fastapiMock, 5000L);
        WireMockSupport.stubDagaonWithdraw(fastapiMock, 5000L);
    }

    @AfterEach
    void tearDownMocks() {
        goalParsingMock.stop();
        spendingMock.stop();
        missionMock.stop();
        executionMock.stop();
        fastapiMock.stop();
    }

    // ── 공통 헬퍼 ──

    protected void cleanupDatabase() {
        transactionRepository.deleteAllInBatch();
        missionRepository.deleteAllInBatch();
        goalRepository.deleteAllInBatch();
        myDataCacheRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        missionCaseRepository.deleteAllInBatch();
        spendingEmbeddingRepository.deleteAllInBatch();
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected String signUpAndLogin() {
        // 회원가입
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup",
                TestFixtures.signUpRequest(),
                String.class
        );
        // 로그인
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                TestFixtures.loginRequest(),
                String.class
        );
        
        try {
            JsonNode root = objectMapper.readTree(loginResponse.getBody());
            return root.path("data").path("token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse login token", e);
        }
    }

    protected <T> T getResponseData(ResponseEntity<String> response, Class<T> valueType) {
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull()) {
                return null;
            }
            return objectMapper.treeToValue(dataNode, valueType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse API response data", e);
        }
    }
}

