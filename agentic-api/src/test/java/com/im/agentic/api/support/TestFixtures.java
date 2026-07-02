package com.im.agentic.api.support;

import com.im.agentic.api.user.dto.UserRequestDto.SignUpRequest;
import com.im.agentic.api.user.dto.UserRequestDto.LoginRequest;
import com.im.agentic.api.goal.dto.GoalRequestDto.CreateGoalRequest;
import com.im.agentic.domain.goal.entity.GoalSlotType;

public class TestFixtures {

    // ── 사용자 ──
    public static final String TEST_EMAIL = "test@im.com";
    public static final String TEST_PASSWORD = "test1234!";

    public static SignUpRequest signUpRequest() {
        SignUpRequest req = new SignUpRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);
        return req;
    }

    public static LoginRequest loginRequest() {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);
        return req;
    }

    // ── 목표 ──
    public static CreateGoalRequest createGoalRequest() {
        CreateGoalRequest req = new CreateGoalRequest();
        req.setItemName("나이키 에어맥스");
        req.setTargetAmount(128000L);
        req.setSlotType(GoalSlotType.SHORT);
        req.setRegistrationType("NATURAL_LANGUAGE");
        req.setRawInput("나이키 에어맥스 사고싶어");
        return req;
    }

    public static CreateGoalRequest createGoalRequestWithoutRawInput() {
        CreateGoalRequest req = new CreateGoalRequest();
        req.setItemName("아이패드 프로");
        req.setTargetAmount(1299000L);
        req.setSlotType(GoalSlotType.MID);
        req.setRegistrationType("MANUAL");
        return req;
    }

    // ── Agent Server Mock 응답 ──
    public static String goalParsingResponse() {
        return """
            {
                "itemName": "나이키 에어맥스",
                "price": 128000,
                "category": "패션"
            }
            """;
    }

    public static String spendingAnalysisResponse() {
        return """
            {
                "categoryExpenses": {"카페": 40000, "식비": 80000, "교통": 30000},
                "totalExpenses": 150000,
                "dailyAvailableAmount": 95000,
                "topCategory": "식비"
            }
            """;
    }

    public static String missionGenerationResponse() {
        return """
            {
                "restraintCategory": "카페",
                "restraintLimitAmount": 4000,
                "restraintContributionRate": 1.8,
                "savingTransferAmount": 3000,
                "savingContributionRate": 2.3
            }
            """;
    }

    public static String executionSuccessResponse() {
        return """
            {
                "success": true,
                "shouldTransfer": true,
                "savedAmount": 500,
                "message": "카페 절제 성공! 500원 절약"
            }
            """;
    }

    // ── iMKRW Mock 응답 ──
    public static String mintTokenResponse(long amount) {
        return String.format("""
            {
                "success": true,
                "mintedAmount": %d,
                "currency": "iMKRW",
                "txHash": "0xtest1234"
            }
            """, amount);
    }

    public static String lockTokenResponse() {
        return """
            {
                "success": true,
                "lockId": "test-lock-id",
                "lockedAmount": 5000,
                "message": "스마트컨트랙트 잠금 완료"
            }
            """;
    }

    public static String unlockTokenResponse() {
        return """
            {
                "success": true,
                "lockId": "test-lock-id",
                "status": "UNLOCKED"
            }
            """;
    }

    public static String burnTokenResponse(long amount) {
        return String.format("""
            {
                "success": true,
                "burnedAmount": %d,
                "wonAmount": %d
            }
            """, amount, amount);
    }

    public static String dagaonWithdrawResponse(long amount) {
        return String.format("""
            {
                "success": true,
                "withdrawAmount": %d,
                "walletAddress": "0xDAGAON0001"
            }
            """, amount);
    }
}
