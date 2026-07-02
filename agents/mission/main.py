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

app = FastAPI(title="iM AgentiX — MissionGenerationAgent")

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
        logger.error(f"소비 검색 실패: {e}")
        return []

def search_similar_missions(query_embedding: list, top_k: int = 3) -> list:
    try:
        conn = get_db_conn()
        cur = conn.cursor()
        cur.execute("""
            SELECT category, mission_type, limit_amount, success_rate
            FROM mission_cases
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """, (str(query_embedding), top_k))
        rows = cur.fetchall()
        conn.close()
        return [{"category": r[0], "missionType": r[1],
                 "limitAmount": r[2], "successRate": r[3]} for r in rows]
    except Exception as e:
        logger.error(f"미션 검색 실패: {e}")
        return []

def save_mission_case(category: str, mission_type: str,
                       limit_amount: int, success_rate: float, embedding: list):
    try:
        conn = get_db_conn()
        cur = conn.cursor()
        content = f"{category} {mission_type} 미션 한도 {limit_amount}원 성공률 {success_rate*100:.0f}%"
        cur.execute("""
            INSERT INTO mission_cases (category, mission_type, limit_amount, success_rate, embedding, content, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s::vector, %s, NOW(), NOW())
        """, (category, mission_type, limit_amount, success_rate, str(embedding), content))
        conn.commit()
        conn.close()
    except Exception as e:
        logger.error(f"미션 사례 저장 실패: {e}")

def clean_json(response: str) -> str:
    response = response.strip()
    if response.startswith("```json"):
        response = response[7:]
    elif response.startswith("```"):
        response = response[3:]
    if response.endswith("```"):
        response = response[:-3]
    return response.strip()

class MissionGenerationRequest(BaseModel):
    userId: int
    categoryExpenses: dict[str, int]
    dailyAvailableAmount: int
    goalItemName: str

class MissionGenerationResponse(BaseModel):
    restraintCategory: str
    restraintLimitAmount: int
    restraintContributionRate: float
    savingTransferAmount: int
    savingContributionRate: float

@app.post("/agent/generate-mission", response_model=MissionGenerationResponse)
async def generate_mission(request: MissionGenerationRequest):
    logger.info(f"[MissionGenerationAgent] userId={request.userId}, goal={request.goalItemName}")

    query = f"소비: {request.categoryExpenses}, 가용: {request.dailyAvailableAmount}원, 목표: {request.goalItemName}"
    embedding = await ollama_embed(query)

    similar_spending = search_similar_spending(request.userId, embedding, top_k=3)
    similar_missions = search_similar_missions(embedding, top_k=3)

    spending_ctx = "\n".join([
        f"- {s['category']} {s['merchantName']}: {s['amount']}원"
        for s in similar_spending
    ]) or "없음"

    mission_ctx = "\n".join([
        f"- [{m['missionType']}] {m['category']}, 성공률 {m['successRate']*100:.0f}%"
        for m in similar_missions
    ]) or "없음"

    prompt = f"""당신은 금융 미션 생성 AI입니다.
반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트 없이 JSON만 출력하세요.
{{
  "restraint": {{"categoryName": "카테고리명", "limitAmount": 숫자, "contributionRate": 소수}},
  "saving": {{"transferAmount": 숫자, "contributionRate": 소수}}
}}

[유사 소비 내역]
{spending_ctx}

[성공 미션 사례]
{mission_ctx}

소비 패턴: {request.categoryExpenses}
가용 금액: {request.dailyAvailableAmount}원
목표: {request.goalItemName}"""

    logger.info(f"[MissionGenerationAgent] Prompt length: {len(prompt)} chars")
    raw = await ollama_generate(prompt)
    logger.info(f"[MissionGenerationAgent] RAG 적용 완료, 소비 {len(similar_spending)}건, 미션 {len(similar_missions)}건")

    try:
        data = json.loads(clean_json(raw))
        r = data["restraint"]
        s = data["saving"]
        result = MissionGenerationResponse(
            restraintCategory=r["categoryName"],
            restraintLimitAmount=int(r["limitAmount"]),
            restraintContributionRate=float(r["contributionRate"]),
            savingTransferAmount=int(s["transferAmount"]),
            savingContributionRate=float(s["contributionRate"])
        )
        # 성공 미션 사례 저장
        emb = await ollama_embed(f"{r['categoryName']} RESTRAINT 미션")
        if emb:
            save_mission_case(r["categoryName"], "RESTRAINT", int(r["limitAmount"]), 0.7, emb)
        return result
    except Exception as e:
        logger.error(f"[MissionGenerationAgent] 파싱 실패: {e}")
        return MissionGenerationResponse(
            restraintCategory="식비", restraintLimitAmount=10000,
            restraintContributionRate=1.0, savingTransferAmount=5000,
            savingContributionRate=0.5
        )

@app.get("/health")
def health():
    return {"status": "ok", "agent": "MissionGenerationAgent", "port": 8003}

class OverageDetectRequest(BaseModel):
    userId: int
    dailyBudget: int
    todaySpend: int
    categoryExpenses: dict[str, int]

class MissionProposal(BaseModel):
    category: str
    description: str
    limitAmount: int
    depositAmount: int
    durationDays: int

class OverageDetectResponse(BaseModel):
    hasOverage: bool
    overageAmount: int
    overageRate: float
    missionCount: int
    missions: list[MissionProposal]

@app.post("/agent/detect-overage", response_model=OverageDetectResponse)
async def detect_overage(request: OverageDetectRequest):
    overage_amount = request.todaySpend - request.dailyBudget
    if overage_amount <= 0:
        return OverageDetectResponse(
            hasOverage=False, overageAmount=0, overageRate=0.0,
            missionCount=0, missions=[]
        )
    overage_rate = overage_amount / request.dailyBudget * 100
    if overage_rate < 25:
        mission_count, duration_days = 1, 3
    elif overage_rate < 50:
        mission_count, duration_days = 2, 7
    else:
        mission_count, duration_days = 3, 14

    sorted_categories = sorted(
        request.categoryExpenses.items(), key=lambda x: x[1], reverse=True
    )
    deposit_per_mission = overage_amount // mission_count
    missions = []
    for i in range(min(mission_count, len(sorted_categories))):
        cat, cat_amount = sorted_categories[i]
        daily_avg = cat_amount // 30
        limit = int(daily_avg * 0.7)
        missions.append(MissionProposal(
            category=cat,
            description=f"{cat} 지출을 {limit:,}원 이하로 줄여보세요",
            limitAmount=max(limit, 1000),
            depositAmount=deposit_per_mission,
            durationDays=duration_days
        ))

    return OverageDetectResponse(
        hasOverage=True,
        overageAmount=overage_amount,
        overageRate=round(overage_rate, 1),
        missionCount=mission_count,
        missions=missions
    )
