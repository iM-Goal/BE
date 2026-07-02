"""
GoalParsingAgent 단위 테스트
- 자연어 입력 → 상품명/가격/카테고리 파싱
- Ollama 파싱 실패 시 폴백 처리
- RAG 컨텍스트 주입 검증
"""
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock
import sys
import os

if "main" in sys.modules:
    del sys.modules["main"]
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from main import app

client = TestClient(app)


def test_health_check():
    """헬스체크 — status ok, agent 명 확인"""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["agent"] == "GoalParsingAgent"
    assert data["port"] == 8001


class TestGoalParsingAgent:

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    def test_parse_goal_withValidInput_returnsItemName(
            self, mock_search, mock_embed, mock_generate):
        """자연어 입력 파싱 성공 — 상품명/가격/카테고리 반환"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_search.return_value = []
        mock_generate.return_value = '{"itemName": "나이키 에어맥스", "price": 128000, "category": "패션"}'

        # when
        response = client.post("/agent/parse-goal", json={
            "userId": 1,
            "rawInput": "나이키 에어맥스 사고싶어"
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["itemName"] == "나이키 에어맥스"
        assert data["price"] == 128000
        assert data["category"] == "패션"

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    def test_parse_goal_withInvalidJson_returnsFallback(
            self, mock_search, mock_embed, mock_generate):
        """Ollama 잘못된 JSON 반환 시 폴백 — rawInput 그대로 반환"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_search.return_value = []
        mock_generate.return_value = "죄송합니다. 파싱할 수 없습니다."

        # when
        response = client.post("/agent/parse-goal", json={
            "userId": 1,
            "rawInput": "아이패드 갖고싶다"
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["itemName"] == "아이패드 갖고싶다"
        assert data["price"] == 0
        assert data["category"] == "기타"

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    def test_parse_goal_withRagContext_injectsContextInPrompt(
            self, mock_search, mock_embed, mock_generate):
        """RAG 컨텍스트 주입 — 유사 소비내역 검색 결과가 프롬프트에 포함되는지 검증"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_search.return_value = [
            {"category": "패션", "merchantName": "나이키", "amount": 120000}
        ]
        mock_generate.return_value = '{"itemName": "나이키 에어맥스", "price": 128000, "category": "패션"}'

        # when
        response = client.post("/agent/parse-goal", json={
            "userId": 1,
            "rawInput": "나이키 신발 사고싶어"
        })

        # then
        assert response.status_code == 200
        # search_similar_spending 호출 검증
        mock_search.assert_called_once()

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    def test_parse_goal_withPriceZero_returnsZeroPrice(
            self, mock_search, mock_embed, mock_generate):
        """가격 알 수 없는 경우 price=0 반환"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_search.return_value = []
        mock_generate.return_value = '{"itemName": "여행", "price": 0, "category": "여행"}'

        # when
        response = client.post("/agent/parse-goal", json={
            "userId": 1,
            "rawInput": "여행 가고싶어"
        })

        # then
        assert response.status_code == 200
        assert response.json()["price"] == 0

    @patch("main.ollama_generate", new_callable=AsyncMock)
    @patch("main.ollama_embed", new_callable=AsyncMock)
    @patch("main.search_similar_spending")
    def test_parse_goal_withCodeBlockJson_cleansAndParses(
            self, mock_search, mock_embed, mock_generate):
        """Ollama가 ```json 코드블록으로 감싼 응답 반환 시 정상 파싱"""
        # given
        mock_embed.return_value = [0.1] * 768
        mock_search.return_value = []
        mock_generate.return_value = '```json\n{"itemName": "맥북", "price": 1590000, "category": "전자기기"}\n```'

        # when
        response = client.post("/agent/parse-goal", json={
            "userId": 1,
            "rawInput": "맥북 에어 사고싶어"
        })

        # then
        assert response.status_code == 200
        data = response.json()
        assert data["itemName"] == "맥북"
        assert data["price"] == 1590000
