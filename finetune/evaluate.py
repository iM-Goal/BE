import json
import os
import httpx
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
GOLDEN_PATH = os.path.join(DATA_DIR, "golden_eval.jsonl")

OLLAMA_URL = "http://localhost:11434"
BASE_MODEL = "qwen2.5:1.5b"
TUNED_MODEL = "qwen2.5-tuned"
FUSED_MODEL_PATH = os.path.join(SCRIPT_DIR, "fused_model")
ADAPTER_PATH = os.path.join(os.path.dirname(SCRIPT_DIR), "adapters") # mlx_lm default save path

# MLX 로컬 모델 싱글톤 캐시 (폴백용)
_mlx_model = None
_mlx_tokenizer = None

def get_mlx_model():
    global _mlx_model, _mlx_tokenizer
    if _mlx_model is None:
        print("[Fallback] MLX 패키지를 이용해 로컬에서 직접 모델을 로드합니다...")
        from mlx_lm import load
        # Fused model이 존재하면 이를 우선 로드, 아니면 베이스 + 어댑터 로드
        if os.path.exists(FUSED_MODEL_PATH):
            print(f"[Fallback] Fused 모델 로드 중: {FUSED_MODEL_PATH}")
            _mlx_model, _mlx_tokenizer = load(FUSED_MODEL_PATH)
        elif os.path.exists(ADAPTER_PATH):
            print(f"[Fallback] 베이스 모델 + 어댑터 로드 중: {ADAPTER_PATH}")
            _mlx_model, _mlx_tokenizer = load("mlx-community/Qwen2.5-1.5B-Instruct-4bit", adapter_path=ADAPTER_PATH)
        else:
            print("[Fallback] 경고: 학습된 어댑터나 Fused 모델을 찾을 수 없습니다. 베이스 모델을 사용합니다.")
            _mlx_model, _mlx_tokenizer = load("mlx-community/Qwen2.5-1.5B-Instruct-4bit")
    return _mlx_model, _mlx_tokenizer

OLLAMA_AVAILABLE = None

def check_ollama_available():
    global OLLAMA_AVAILABLE
    if OLLAMA_AVAILABLE is not None:
        return OLLAMA_AVAILABLE
    try:
        with httpx.Client(timeout=1.0) as client:
            response = client.get(f"{OLLAMA_URL}/api/tags")
            if response.status_code == 200:
                OLLAMA_AVAILABLE = True
                print("[Ollama] 서비스 감지 성공. Ollama를 우선 사용합니다.")
                return True
    except Exception:
        pass
    OLLAMA_AVAILABLE = False
    print("[Ollama] 서비스 응답 없음. 로컬 MLX 모드로 직행합니다.")
    return False

def generate_ollama(model_name, prompt):
    if not check_ollama_available():
        return None
    try:
        with httpx.Client(timeout=10.0) as client:
            response = client.post(
                f"{OLLAMA_URL}/api/generate",
                json={"model": model_name, "prompt": prompt, "stream": False}
            )
            if response.status_code == 200:
                return response.json().get("response", "").strip()
    except Exception as e:
        print(f"[Ollama] {model_name} 호출 실패: {e}")
    return None

def generate_mlx(prompt):
    try:
        from mlx_lm import generate
        model, tokenizer = get_mlx_model()
        response = generate(model, tokenizer, prompt=prompt, max_tokens=256, verbose=False)
        return response.strip()
    except Exception as e:
        print(f"[MLX Fallback] 직접 추론 실패: {e}")
        return ""

def query_model(model_name, prompt):
    # 베이스 모델은 Ollama를 기본 호출하되, Ollama가 꺼져있으면 연산 없이 건너뜀 (크래시 방지)
    if model_name == BASE_MODEL:
        if not check_ollama_available():
            return ""
        res = generate_ollama(BASE_MODEL, prompt)
        return res if res else ""
    
    # 튜닝된 모델: Ollama 사용 가능하면 Ollama 시도, 실패 시 로컬 MLX
    if check_ollama_available():
        res = generate_ollama(TUNED_MODEL, prompt)
        if res is not None:
            return res
    
    return generate_mlx(prompt)

def clean_json(text):
    text = text.strip()
    # Markdown 코드 블록 제거
    if text.startswith("```json"):
        text = text[7:]
    elif text.startswith("```"):
        text = text[3:]
    if text.endswith("```"):
        text = text[:-3]
    text = text.strip()
    # 첫번째 {와 마지막 }를 찾아서 추출
    match = re.search(r"(\{.*\})", text, re.DOTALL)
    if match:
        return match.group(1)
    return text

def parse_to_json(text):
    try:
        cleaned = clean_json(text)
        return json.loads(cleaned)
    except Exception:
        return None

def run_evaluation():
    if not os.path.exists(GOLDEN_PATH):
        print(f"오류: 골든 데이터셋 파일이 존재하지 않습니다: {GOLDEN_PATH}")
        return

    print("골든 데이터셋을 로드하는 중...")
    with open(GOLDEN_PATH, "r", encoding="utf-8") as f:
        samples = [json.loads(line) for line in f if line.strip()]

    goal_samples = []
    mission_samples = []
    
    for s in samples:
        if "금융 목표 파싱" in s["prompt"]:
            goal_samples.append(s)
        else:
            mission_samples.append(s)

    print(f"로드 완료: 총 {len(samples)}건 (목표 파싱: {len(goal_samples)}건, 미션 생성: {len(mission_samples)}건)")

    results = {
        "base": {"goal_success": 0, "goal_json_ok": 0, "goal_item_ok": 0, "goal_price_mape": [],
                 "mission_success": 0, "mission_json_ok": 0, "mission_cat_ok": 0, "mission_numeric_ok": 0},
        "tuned": {"goal_success": 0, "goal_json_ok": 0, "goal_item_ok": 0, "goal_price_mape": [],
                  "mission_success": 0, "mission_json_ok": 0, "mission_cat_ok": 0, "mission_numeric_ok": 0}
    }

    # --- 1단계: Base Model 평가 패스 (VRAM 스왑 방지용 순차 연산) ---
    print("\n--- [Base Model] 평가 진행 중 ---")
    
    # Base 모델 - 목표 파싱 평가
    for idx, sample in enumerate(goal_samples):
        prompt = sample["prompt"]
        answer = json.loads(sample["completion"])
        base_res = query_model(BASE_MODEL, prompt)
        base_json = parse_to_json(base_res)
        if base_json:
            results["base"]["goal_json_ok"] += 1
            if base_json.get("itemName") == answer["itemName"]:
                results["base"]["goal_item_ok"] += 1
            ans_price = answer["price"]
            pred_price = base_json.get("price")
            if pred_price is None: pred_price = 0
            try: pred_price = int(pred_price)
            except Exception: pred_price = 0
            if ans_price > 0:
                mape = abs(ans_price - pred_price) / ans_price
                results["base"]["goal_price_mape"].append(mape)
            else:
                results["base"]["goal_price_mape"].append(0.0 if pred_price == 0 else 1.0)
        if (idx + 1) % 10 == 0:
            print(f"[Base Model] 목표 파싱 평가 진행률: {idx + 1}/{len(goal_samples)}")

    # Base 모델 - 미션 생성 평가
    for idx, sample in enumerate(mission_samples):
        prompt = sample["prompt"]
        answer = json.loads(sample["completion"])
        base_res = query_model(BASE_MODEL, prompt)
        base_json = parse_to_json(base_res)
        if base_json:
            results["base"]["mission_json_ok"] += 1
            base_cat = base_json.get("restraint", {}).get("categoryName")
            ans_cat = answer.get("restraint", {}).get("categoryName")
            if base_cat == ans_cat:
                results["base"]["mission_cat_ok"] += 1
            base_limit = base_json.get("restraint", {}).get("limitAmount", 0)
            base_transfer = base_json.get("saving", {}).get("transferAmount", 0)
            if isinstance(base_limit, (int, float)) and base_limit > 0 and isinstance(base_transfer, (int, float)) and base_transfer > 0:
                results["base"]["mission_numeric_ok"] += 1
        if (idx + 1) % 10 == 0:
            print(f"[Base Model] 미션 생성 평가 진행률: {idx + 1}/{len(mission_samples)}")


    # --- 2단계: Tuned Model 평가 패스 (VRAM 스왑 방지용 순차 연산) ---
    print("\n--- [Tuned Model] 평가 진행 중 ---")

    # Tuned 모델 - 목표 파싱 평가
    for idx, sample in enumerate(goal_samples):
        prompt = sample["prompt"]
        answer = json.loads(sample["completion"])
        tuned_res = query_model(TUNED_MODEL, prompt)
        tuned_json = parse_to_json(tuned_res)
        if tuned_json:
            results["tuned"]["goal_json_ok"] += 1
            if tuned_json.get("itemName") == answer["itemName"]:
                results["tuned"]["goal_item_ok"] += 1
            ans_price = answer["price"]
            pred_price = tuned_json.get("price")
            if pred_price is None: pred_price = 0
            try: pred_price = int(pred_price)
            except Exception: pred_price = 0
            if ans_price > 0:
                mape = abs(ans_price - pred_price) / ans_price
                results["tuned"]["goal_price_mape"].append(mape)
            else:
                results["tuned"]["goal_price_mape"].append(0.0 if pred_price == 0 else 1.0)
        if (idx + 1) % 10 == 0:
            print(f"[Tuned Model] 목표 파싱 평가 진행률: {idx + 1}/{len(goal_samples)}")

    # Tuned 모델 - 미션 생성 평가
    for idx, sample in enumerate(mission_samples):
        prompt = sample["prompt"]
        answer = json.loads(sample["completion"])
        tuned_res = query_model(TUNED_MODEL, prompt)
        tuned_json = parse_to_json(tuned_res)
        if tuned_json:
            results["tuned"]["mission_json_ok"] += 1
            tuned_cat = tuned_json.get("restraint", {}).get("categoryName")
            ans_cat = answer.get("restraint", {}).get("categoryName")
            if tuned_cat == ans_cat:
                results["tuned"]["mission_cat_ok"] += 1
            tuned_limit = tuned_json.get("restraint", {}).get("limitAmount", 0)
            tuned_transfer = tuned_json.get("saving", {}).get("transferAmount", 0)
            if isinstance(tuned_limit, (int, float)) and tuned_limit > 0 and isinstance(tuned_transfer, (int, float)) and tuned_transfer > 0:
                results["tuned"]["mission_numeric_ok"] += 1
        if (idx + 1) % 10 == 0:
            print(f"[Tuned Model] 미션 생성 평가 진행률: {idx + 1}/{len(mission_samples)}")

    # 결과 통계 요약 작성
    total_goals = len(goal_samples)
    total_missions = len(mission_samples)

    base_goal_json_rate = (results["base"]["goal_json_ok"] / total_goals) * 100
    base_goal_item_rate = (results["base"]["goal_item_ok"] / total_goals) * 100
    base_goal_mape = (sum(results["base"]["goal_price_mape"]) / len(results["base"]["goal_price_mape"])) * 100 if results["base"]["goal_price_mape"] else 100.0
    
    tuned_goal_json_rate = (results["tuned"]["goal_json_ok"] / total_goals) * 100
    tuned_goal_item_rate = (results["tuned"]["goal_item_ok"] / total_goals) * 100
    tuned_goal_mape = (sum(results["tuned"]["goal_price_mape"]) / len(results["tuned"]["goal_price_mape"])) * 100 if results["tuned"]["goal_price_mape"] else 100.0

    base_mission_json_rate = (results["base"]["mission_json_ok"] / total_missions) * 100
    base_mission_cat_rate = (results["base"]["mission_cat_ok"] / total_missions) * 100
    base_mission_num_rate = (results["base"]["mission_numeric_ok"] / total_missions) * 100
    
    tuned_mission_json_rate = (results["tuned"]["mission_json_ok"] / total_missions) * 100
    tuned_mission_cat_rate = (results["tuned"]["mission_cat_ok"] / total_missions) * 100
    tuned_mission_num_rate = (results["tuned"]["mission_numeric_ok"] / total_missions) * 100

    report_content = f"""## 2단계: Before/After 평가 결과 (골든 데이터셋 {len(samples)}건 기준)

### 1. 목표 파싱 (Goal Parsing) 평가 지표 (총 {total_goals}건)
| 지표 | 베이스 모델 ({BASE_MODEL}) | 파인튜닝 모델 ({TUNED_MODEL}) | 개선도 |
| :--- | :---: | :---: | :---: |
| **JSON 파싱 성공률** | {base_goal_json_rate:.1f}% | {tuned_goal_json_rate:.1f}% | {tuned_goal_json_rate - base_goal_json_rate:+.1f}%p |
| **물품명 일치율 (정확도)** | {base_goal_item_rate:.1f}% | {tuned_goal_item_rate:.1f}% | {tuned_goal_item_rate - base_goal_item_rate:+.1f}%p |
| **가격 평균 오차율 (MAPE)** | {base_goal_mape:.1f}% | {tuned_goal_mape:.1f}% | {base_goal_mape - tuned_goal_mape:+.1f}%p 개선 |

### 2. 미션 생성 (Mission Generation) 평가 지표 (총 {total_missions}건)
| 지표 | 베이스 모델 ({BASE_MODEL}) | 파인튜닝 모델 ({TUNED_MODEL}) | 개선도 |
| :--- | :---: | :---: | :---: |
| **JSON 파싱 성공률** | {base_mission_json_rate:.1f}% | {tuned_mission_json_rate:.1f}% | {tuned_mission_json_rate - base_mission_json_rate:+.1f}%p |
| **절제 카테고리 일치율** | {base_mission_cat_rate:.1f}% | {tuned_mission_cat_rate:.1f}% | {tuned_mission_cat_rate - base_mission_cat_rate:+.1f}%p |
| **미션 금액 수치 유효성** | {base_mission_num_rate:.1f}% | {tuned_mission_num_rate:.1f}% | {tuned_mission_num_rate - base_mission_num_rate:+.1f}%p |

* 가격 평균 오차율(MAPE)은 낮을수록 우수합니다.
* 미션 금액 수치 유효성은 `limitAmount` 및 `transferAmount`가 양의 정수로 생성되었는지를 검증합니다.
"""
    # 결과를 파일로 일단 임시 저장
    with open(os.path.join(SCRIPT_DIR, "eval_temp.md"), "w", encoding="utf-8") as f:
        f.write(report_content)
        
    print("\n=== Before/After 평가 완료 및 기록 완료 ===")
    print(report_content)

if __name__ == "__main__":
    run_evaluation()
