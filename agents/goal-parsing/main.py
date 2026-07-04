from fastapi import FastAPI
from pydantic import BaseModel
import httpx
import json
import psycopg2
from pgvector.psycopg2 import register_vector
import os
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="iM AgentiX — GoalParsingAgent")

OLLAMA_URL = os.getenv("OLLAMA_BASE_URL", "http://host.docker.internal:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:1.5b")
DB_HOST = os.getenv("DB_HOST", "agentic-postgres")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "agentic")
DB_USER = os.getenv("DB_USER", "agentic_user")
DB_PASS = os.getenv("DB_PASS", "agentic_password")

def get_db_conn():
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT,
        dbname=DB_NAME, user=DB_USER, password=DB_PASS
    )
    register_vector(conn)
    return conn

async def ollama_generate(prompt: str) -> str:
    async with httpx.AsyncClient(timeout=180.0) as client:
        response = await client.post(
            f"{OLLAMA_URL}/api/generate",
            json={"model": OLLAMA_MODEL, "prompt": prompt, "stream": False, "format": "json"}
        )
        return response.json().get("response", "")

async def ollama_embed(text: str) -> list[float]:
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            f"{OLLAMA_URL}/api/embeddings",
            json={"model": "nomic-embed-text", "prompt": text}
        )
        return response.json().get("embedding", [])

def search_similar_spending(user_id: int, query_embedding: list, top_k: int = 3) -> list:
    try:
        conn = get_db_conn()
        cur = conn.cursor()
        cur.execute("""
            SELECT category, merchant_name, amount
            FROM spending_embeddings
            WHERE user_id = %s
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """, (user_id, str(query_embedding), top_k))
        rows = cur.fetchall()
        conn.close()
        return [{"category": r[0], "merchantName": r[1], "amount": r[2]} for r in rows]
    except Exception as e:
        logger.error(f"pgvector 검색 실패: {e}")
        return []

def clean_json(response: str) -> str:
    response = response.strip()
    if response.startswith("```json"):
        response = response[7:]
    elif response.startswith("```"):
        response = response[3:]
    if response.endswith("```"):
        response = response[:-3]
    return response.strip()

SAVABLE_RATIO = {
    1: 0.05,
    2: 0.15,
    3: 0.25,
    4: 0.33,
    5: 0.45,
}

def calculate_feasibility(
    target_price: int,
    duration_days: int,
    income_quintile: int,
    daily_available: int
) -> dict:
    savable_ratio = SAVABLE_RATIO.get(income_quintile, 0.25)
    max_daily_saving = daily_available * savable_ratio
    daily_saving_needed = target_price / duration_days if duration_days > 0 else 0
    feasible = daily_saving_needed <= max_daily_saving
    min_duration = int(target_price / max_daily_saving) + 1 if max_daily_saving > 0 else 9999

    return {
        "dailySavingNeeded": round(daily_saving_needed),
        "maxDailySaving": round(max_daily_saving),
        "feasible": feasible,
        "recommendedDurationDays": duration_days if feasible else min_duration,
        "source": "통계청 2024 Q4 가계동향조사 소득분위별 저축가능비율"
    }

class GoalParsingRequest(BaseModel):
    userId: int
    rawInput: str
    incomeQuintile: int = 3
    dailyAvailable: int = 0

class GoalParsingResponse(BaseModel):
    itemName: str
    price: int
    category: str
    feasibility: dict = None

@app.post("/agent/parse-goal", response_model=GoalParsingResponse)
async def parse_goal(request: GoalParsingRequest):
    logger.info(f"[GoalParsingAgent] userId={request.userId}, input={request.rawInput}, incomeQuintile={request.incomeQuintile}, dailyAvailable={request.dailyAvailable}")

    query_embedding = await ollama_embed(request.rawInput)
    similar = search_similar_spending(request.userId, query_embedding, top_k=3)
    context = "\n".join([f"- {s['category']} {s['merchantName']}: {s['amount']}원" for s in similar]) or "없음"

    prompt = f"""당신은 금융 목표 파싱 AI입니다.
사용자의 입력에서 물품명과 가격을 추출해 반드시 아래 JSON 형식으로만 응답하세요.
{{"itemName": "물품명", "price": 숫자, "category": "카테고리"}}
가격을 모르면 price를 0으로 설정하세요.

[참고: 유사 소비 내역]
{context}

[입력]
{request.rawInput}"""

    logger.info(f"[GoalParsingAgent] Prompt length: {len(prompt)} chars")
    raw = await ollama_generate(prompt)
    logger.info(f"[GoalParsingAgent] 응답: {raw}")

    try:
        data = json.loads(clean_json(raw))
        item_name = data.get("itemName", request.rawInput)
        price = int(data.get("price", 0))
        category = data.get("category", "기타")
        
        # 30일 기준으로 달성 가능 여부 계산
        feasibility = calculate_feasibility(price, 30, request.incomeQuintile, request.dailyAvailable)
        
        return GoalParsingResponse(
            itemName=item_name,
            price=price,
            category=category,
            feasibility=feasibility
        )
    except Exception as e:
        logger.error(f"[GoalParsingAgent] 파싱 실패: {e}")
        feasibility = calculate_feasibility(0, 30, request.incomeQuintile, request.dailyAvailable)
        return GoalParsingResponse(itemName=request.rawInput, price=0, category="기타", feasibility=feasibility)

@app.get("/health")
def health():
    return {"status": "ok", "agent": "GoalParsingAgent", "port": 8001}
