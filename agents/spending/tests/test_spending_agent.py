"""
SpendingAnalysisAgent 단위 테스트
- 소비 내역 카테고리별 집계
- pgvector 임베딩 저장
- 가용 금액 계산
"""
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock, MagicMock
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
    assert response.json()["agent"] == "SpendingAnalysisAgent"
    assert response.json()["port"] == 8002


class TestSpendingAnalysisAgent:

    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.get_db_conn")
    def test_analyze_spending_withMultipleRecords_aggregatesByCategory(
            self, mock_db, mock_embed):
        """카테고리별 집계 — 카페 2건 합산, 식비 1건 반환"""
        # given
        mock_embed.return_value = [0.1] * 768
        spendings = [
            {"category": "카페", "amount": 5000, "merchantName": "스타벅스", "date": "2026-06-19"},
            {"category": "식비", "amount": 12000, "merchantName": "맥도날드", "date": "2026-06-19"},
            {"category": "카페", "amount": 4500, "merchantName": "메가커피", "date": "2026-06-18"},
        ]

        # when
        response = client.post("/agent/analyze-spending", json={
            "userId": 1,
            "spendings": spendings
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["categoryExpenses"]["카페"] == 9500
        assert data["categoryExpenses"]["식비"] == 12000
        assert data["totalExpenses"] == 21500
        assert data["topCategory"] == "식비"

    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.get_db_conn")
    def test_analyze_spending_withEmptyList_returnsZero(
            self, mock_db, mock_embed):
        """빈 소비 내역 — totalExpenses=0, dailyAvailable 계산"""
        # when
        response = client.post("/agent/analyze-spending", json={
            "userId": 1,
            "spendings": []
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["totalExpenses"] == 0
        assert data["dailyAvailableAmount"] == 100000  # (3000000 + 600000 - 0) / 30

    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.get_db_conn")
    def test_analyze_spending_savesEmbeddingsToVectorDB(
            self, mock_db, mock_embed):
        """소비 내역 임베딩 저장 — execute 호출 검증"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_cursor = MagicMock()
        mock_db.return_value.cursor.return_value = mock_cursor
        spendings = [
            {"category": "카페", "amount": 5000, "merchantName": "스타벅스", "date": "2026-06-19"},
            {"category": "식비", "amount": 8000, "merchantName": "버거킹", "date": "2026-06-19"},
        ]

        # when
        client.post("/agent/analyze-spending", json={"userId": 1, "spendings": spendings})

        # then — 임베딩 저장 2회 호출 검증
        assert mock_cursor.execute.call_count == 2

    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.get_db_conn")
    def test_analyze_spending_calculatesTopCategory(
            self, mock_db, mock_embed):
        """최고 지출 카테고리 계산 — 배달음식이 가장 많으면 topCategory=배달음식"""
        # given
        mock_embed.return_value = [0.1] * 768
        spendings = [
            {"category": "카페", "amount": 5000, "merchantName": "스타벅스", "date": "2026-06-19"},
            {"category": "배달음식", "amount": 50000, "merchantName": "배민", "date": "2026-06-19"},
        ]

        # when
        response = client.post("/agent/analyze-spending", json={
            "userId": 1,
            "spendings": spendings
        })

        # then
        assert response.json()["topCategory"] == "배달음식"

    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.get_db_conn")
    def test_analyze_spending_limitsEmbeddingTo20Records(
            self, mock_db, mock_embed):
        """임베딩 저장 최대 20건 제한"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_cursor = MagicMock()
        mock_db.return_value.cursor.return_value = mock_cursor
        spendings = [
            {"category": "카페", "amount": 5000, "merchantName": f"카페{i}", "date": "2026-06-19"}
            for i in range(30)
        ]

        # when
        client.post("/agent/analyze-spending", json={"userId": 1, "spendings": spendings})

        # then — 최대 20건만 저장
        assert mock_cursor.execute.call_count <= 20
