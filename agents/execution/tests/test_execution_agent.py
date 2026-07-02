"""
ExecutionAgent 단위 테스트
- RESTRAINT 미션: 한도 이하 소비 시 성공, 초과 시 실패
- SAVING 미션: 항상 성공
- 절감액 계산 검증
"""
import pytest
from fastapi.testclient import TestClient
import sys
import os

if "main" in sys.modules:
    del sys.modules["main"]
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from main import app

client = TestClient(app)


def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["agent"] == "ExecutionAgent"
    assert response.json()["port"] == 8004


class TestExecutionAgent:

    def test_restraint_withSpendingUnderLimit_returnsSuccess(self):
        """절제형 미션 — 한도 이하 소비 시 성공 + 절감액 반환"""
        # given / when
        response = client.post("/agent/execute", json={
            "userId": 1,
            "goalId": 1,
            "amount": 4000,
            "missionType": "RESTRAINT",
            "categoryName": "카페",
            "limitAmount": 4000,
            "actualSpending": 3500
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert data["shouldTransfer"] is True
        assert data["savedAmount"] == 500  # 4000 - 3500

    def test_restraint_withSpendingOverLimit_returnsFail(self):
        """절제형 미션 — 한도 초과 소비 시 실패 + 이체 안 함"""
        # given / when
        response = client.post("/agent/execute", json={
            "userId": 1,
            "goalId": 1,
            "amount": 4000,
            "missionType": "RESTRAINT",
            "categoryName": "카페",
            "limitAmount": 4000,
            "actualSpending": 7000
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is False
        assert data["shouldTransfer"] is False
        assert data["savedAmount"] == 0

    def test_restraint_withSpendingExactlyAtLimit_returnsSuccess(self):
        """절제형 미션 — 소비 = 한도 정확히 일치 시 성공 (경계값)"""
        # given / when
        response = client.post("/agent/execute", json={
            "userId": 1,
            "goalId": 1,
            "amount": 4000,
            "missionType": "RESTRAINT",
            "categoryName": "카페",
            "limitAmount": 4000,
            "actualSpending": 4000
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert data["savedAmount"] == 0  # 절감액 없음

    def test_saving_alwaysReturnsSuccess(self):
        """저축형 미션 — 항상 성공, amount 그대로 반환"""
        # given / when
        response = client.post("/agent/execute", json={
            "userId": 1,
            "goalId": 1,
            "amount": 3000,
            "missionType": "SAVING"
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert data["shouldTransfer"] is True
        assert data["savedAmount"] == 3000

    def test_saving_withZeroAmount_returnsSuccess(self):
        """저축형 미션 — 0원도 성공 처리 (경계값)"""
        # given / when
        response = client.post("/agent/execute", json={
            "userId": 1,
            "goalId": 1,
            "amount": 0,
            "missionType": "SAVING"
        })

        # then
        assert response.status_code == 200
        assert response.json()["success"] is True

    def test_restraint_withNoActualSpending_treatsAsZero(self):
        """절제형 미션 — actualSpending 없을 때 0으로 처리"""
        # given / when
        response = client.post("/agent/execute", json={
            "userId": 1,
            "goalId": 1,
            "amount": 4000,
            "missionType": "RESTRAINT",
            "categoryName": "카페",
            "limitAmount": 4000
            # actualSpending 없음
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert data["savedAmount"] == 4000  # 4000 - 0
