"""
MissionGenerationAgent 단위 테스트
- RAG 파이프라인 통한 미션 생성
- 유사 소비내역 + 미션 사례 검색 검증
- Ollama 파싱 실패 시 기본값 반환
"""
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock, call
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
    assert response.json()["agent"] == "MissionGenerationAgent"
    assert response.json()["port"] == 8003


class TestMissionGenerationAgent:

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    @patch("main.search_similar_missions")
    @patch("main.save_mission_case")
    def test_generate_mission_withValidInput_returnsTwoMissions(
            self, mock_save, mock_missions, mock_spending, mock_embed, mock_generate):
        """미션 생성 성공 — 절제형 + 저축형 반환"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_spending.return_value = []
        mock_missions.return_value = []
        mock_generate.return_value = '''{
            "restraint": {"categoryName": "카페", "limitAmount": 4000, "contributionRate": 1.8},
            "saving": {"transferAmount": 3000, "contributionRate": 2.3}
        }'''

        # when
        response = client.post("/agent/generate-mission", json={
            "userId": 1,
            "categoryExpenses": {"카페": 40000, "식비": 80000},
            "dailyAvailableAmount": 10000,
            "goalItemName": "나이키 에어맥스"
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["restraintCategory"] == "카페"
        assert data["restraintLimitAmount"] == 4000
        assert data["restraintContributionRate"] == 1.8
        assert data["savingTransferAmount"] == 3000
        assert data["savingContributionRate"] == 2.3

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    @patch("main.search_similar_missions")
    @patch("main.save_mission_case")
    def test_generate_mission_withInvalidJson_returnsDefaultMission(
            self, mock_save, mock_missions, mock_spending, mock_embed, mock_generate):
        """Ollama 파싱 실패 시 기본값 반환 — 식비/10000원/5000원"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_spending.return_value = []
        mock_missions.return_value = []
        mock_generate.return_value = "파싱 불가"

        # when
        response = client.post("/agent/generate-mission", json={
            "userId": 1,
            "categoryExpenses": {"식비": 100000},
            "dailyAvailableAmount": 5000,
            "goalItemName": "아이패드"
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["restraintCategory"] == "식비"
        assert data["restraintLimitAmount"] == 10000
        assert data["savingTransferAmount"] == 5000

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    @patch("main.search_similar_missions")
    @patch("main.save_mission_case")
    def test_generate_mission_searchesVectorDB(
            self, mock_save, mock_missions, mock_spending, mock_embed, mock_generate):
        """RAG 파이프라인 — pgvector 유사 소비내역 + 미션 사례 검색 호출 검증"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_spending.return_value = [
            {"category": "카페", "merchantName": "스타벅스", "amount": 5000}
        ]
        mock_missions.return_value = [
            {"category": "카페", "missionType": "RESTRAINT", "limitAmount": 4000, "successRate": 0.7}
        ]
        mock_generate.return_value = '''{
            "restraint": {"categoryName": "카페", "limitAmount": 4000, "contributionRate": 1.8},
            "saving": {"transferAmount": 3000, "contributionRate": 2.3}
        }'''

        # when
        client.post("/agent/generate-mission", json={
            "userId": 1,
            "categoryExpenses": {"카페": 40000},
            "dailyAvailableAmount": 10000,
            "goalItemName": "나이키"
        })

        # then — 유사 소비내역 검색 + 미션 사례 검색 각 1회 호출
        mock_spending.assert_called_once()
        mock_missions.assert_called_once()

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    @patch("main.search_similar_missions")
    @patch("main.save_mission_case")
    def test_generate_mission_savesMissionCaseAfterGeneration(
            self, mock_save, mock_missions, mock_spending, mock_embed, mock_generate):
        """성공 미션 사례 pgvector 저장 검증"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_spending.return_value = []
        mock_missions.return_value = []
        mock_generate.return_value = '''{
            "restraint": {"categoryName": "카페", "limitAmount": 4000, "contributionRate": 1.8},
            "saving": {"transferAmount": 3000, "contributionRate": 2.3}
        }'''

        # when
        client.post("/agent/generate-mission", json={
            "userId": 1,
            "categoryExpenses": {"카페": 40000},
            "dailyAvailableAmount": 10000,
            "goalItemName": "나이키"
        })

        # then — 미션 사례 저장 1회 호출
        mock_save.assert_called_once()
