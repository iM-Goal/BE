package com.im.agentic.api.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class WireMockSupport {

    // ── Agent Server (8001~8004) Mock ──

    public static void stubGoalParsingAgent(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/agent/parse-goal"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.goalParsingResponse())));
    }

    public static void stubSpendingAnalysisAgent(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/agent/analyze-spending"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.spendingAnalysisResponse())));
    }

    public static void stubMissionGenerationAgent(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/agent/generate-mission"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.missionGenerationResponse())));
    }

    public static void stubExecutionAgent(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/agent/execute"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.executionSuccessResponse())));
    }

    // ── FastAPI Mock (iMKRW + 마이데이터) ──

    public static void stubMintToken(WireMockServer server, long amount) {
        server.stubFor(post(urlEqualTo("/mock/imkrw/mint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.mintTokenResponse(amount))));
    }

    public static void stubLockToken(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/mock/kaia/lock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.lockTokenResponse())));
    }

    public static void stubUnlockToken(WireMockServer server) {
        server.stubFor(post(urlMatching("/mock/kaia/unlock/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.unlockTokenResponse())));
    }

    public static void stubBurnToken(WireMockServer server, long amount) {
        server.stubFor(post(urlEqualTo("/mock/imkrw/burn"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.burnTokenResponse(amount))));
    }

    public static void stubDagaonWithdraw(WireMockServer server, long amount) {
        server.stubFor(post(urlEqualTo("/mock/dagaon/withdraw"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(TestFixtures.dagaonWithdrawResponse(amount))));
    }

    public static void stubSpendingHistory(WireMockServer server) {
        server.stubFor(get(urlMatching("/mock/spending/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                            {
                                "spending": [
                                    {"category": "카페", "amount": 5000, "merchantName": "스타벅스", "date": "2026-06-19"},
                                    {"category": "식비", "amount": 12000, "merchantName": "맥도날드", "date": "2026-06-19"},
                                    {"category": "카페", "amount": 4500, "merchantName": "메가커피", "date": "2026-06-18"}
                                ]
                            }
                            """)));
    }
}
