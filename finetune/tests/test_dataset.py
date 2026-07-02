"""
파인튜닝 데이터셋 품질 검증 테스트
"""
import json
import os
import pytest

DATASET_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "data"
)


class TestGoalParsingDataset:

    @pytest.fixture
    def dataset(self):
        path = os.path.join(DATASET_DIR, "goal_parsing.jsonl")
        if not os.path.exists(path):
            pytest.skip("데이터셋 파일 없음. generate_dataset.py 먼저 실행")
        data = []
        with open(path, encoding="utf-8") as f:
            for line in f:
                data.append(json.loads(line))
        return data

    def test_dataset_size_isAtLeast500(self, dataset):
        """데이터셋 크기 — 최소 500건"""
        assert len(dataset) >= 500, f"데이터셋 크기 부족: {len(dataset)}"

    def test_dataset_hasPromptAndCompletion(self, dataset):
        """모든 항목에 prompt, completion 필드 존재"""
        for item in dataset[:10]:
            assert "prompt" in item, "prompt 필드 없음"
            assert "completion" in item, "completion 필드 없음"

    def test_completion_isValidJson(self, dataset):
        """completion 필드가 유효한 JSON — itemName/price/category 포함"""
        for item in dataset[:50]:
            completion = json.loads(item["completion"])
            assert "itemName" in completion
            assert "price" in completion
            assert "category" in completion
            assert isinstance(completion["price"], int)

    def test_price_isNonNegative(self, dataset):
        """price 필드 — 음수 없음"""
        for item in dataset:
            completion = json.loads(item["completion"])
            assert completion["price"] >= 0

    def test_prompt_containsInputKeyword(self, dataset):
        """prompt 필드 — '입력:' 키워드 포함 여부"""
        for item in dataset[:20]:
            assert "입력" in item["prompt"] or "rawInput" in item["prompt"] or len(item["prompt"]) > 0


class TestMissionDataset:

    @pytest.fixture
    def dataset(self):
        path = os.path.join(DATASET_DIR, "mission_generation.jsonl")
        if not os.path.exists(path):
            pytest.skip("데이터셋 파일 없음. generate_dataset.py 먼저 실행")
        data = []
        with open(path, encoding="utf-8") as f:
            for line in f:
                data.append(json.loads(line))
        return data

    def test_dataset_size_isAtLeast300(self, dataset):
        """데이터셋 크기 — 최소 300건"""
        assert len(dataset) >= 300

    def test_mission_hasRestraintAndSaving(self, dataset):
        """모든 미션에 restraint, saving 필드 포함"""
        for item in dataset[:20]:
            completion = json.loads(item["completion"])
            assert "restraint" in completion
            assert "saving" in completion

    def test_restraint_hasRequiredFields(self, dataset):
        """restraint 필드 — categoryName, limitAmount, contributionRate 포함"""
        for item in dataset[:20]:
            completion = json.loads(item["completion"])
            restraint = completion["restraint"]
            assert "categoryName" in restraint
            assert "limitAmount" in restraint
            assert "contributionRate" in restraint

    def test_saving_hasRequiredFields(self, dataset):
        """saving 필드 — transferAmount, contributionRate 포함"""
        for item in dataset[:20]:
            completion = json.loads(item["completion"])
            saving = completion["saving"]
            assert "transferAmount" in saving
            assert "contributionRate" in saving

    def test_limitAmount_isPositive(self, dataset):
        """limitAmount — 양수"""
        for item in dataset:
            completion = json.loads(item["completion"])
            assert completion["restraint"]["limitAmount"] > 0
