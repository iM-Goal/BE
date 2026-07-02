from fastapi import FastAPI
from pydantic import BaseModel
import httpx
import psycopg2
from pgvector.psycopg2 import register_vector
import os
import logging
import asyncio

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="iM AgentiX — SpendingAnalysisAgent")

OLLAMA_URL = os.getenv("OLLAMA_BASE_URL", "http://host.docker.internal:11434")
DB_HOST = os.getenv("DB_HOST", "agentic-postgres")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "agentic")
DB_USER = os.getenv("DB_USER", "agentic_user")
DB_PASS = os.getenv("DB_PASS", "agentic_password")

# 글로벌 HTTP 클라이언트 재사용 (TCP handshake 및 소켓 오버헤드 방지)
http_client = httpx.AsyncClient(timeout=10.0)

def get_db_conn():
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT,
        dbname=DB_NAME, user=DB_USER, password=DB_PASS
    )
    register_vector(conn)
    return conn

async def ollama_embed(text: str) -> list[float]:
    try:
        response = await http_client.post(
            f"{OLLAMA_URL}/api/embeddings",
            json={"model": "nomic-embed-text", "prompt": text}
        )
        return response.json().get("embedding", [])
    except Exception as e:
        logger.error(f"임베딩 생성 실패: {e}")
        return []

class SpendingRecord(BaseModel):
    category: str
    amount: int
    merchantName: str
    date: str

class SpendingAnalysisRequest(BaseModel):
    userId: int
    spendings: list[SpendingRecord]

class SpendingAnalysisResponse(BaseModel):
    categoryExpenses: dict[str, int]
    totalExpenses: int
    dailyAvailableAmount: int
    topCategory: str

@app.post("/agent/analyze-spending", response_model=SpendingAnalysisResponse)
async def analyze_spending(request: SpendingAnalysisRequest):
    logger.info(f"[SpendingAnalysisAgent] userId={request.userId}, records={len(request.spendings)}")

    category_expenses = {}
    for record in request.spendings:
        category_expenses[record.category] = \
            category_expenses.get(record.category, 0) + record.amount

    total = sum(category_expenses.values())
    income = max(3000000, total + 600000)
    daily = (income - total) // 30
    top = max(category_expenses, key=category_expenses.get) if category_expenses else "식비"

    # 1. 20개 레코드 임베딩 병렬 생성 (HTTP 커넥션 재사용)
    async def get_embedding(rec):
        content = f"{rec.category} {rec.merchantName} {rec.amount}원"
        emb = await ollama_embed(content)
        return (rec, emb)

    results = await asyncio.gather(*(get_embedding(r) for r in request.spendings[:20]))

    # 2. 단일 커넥션으로 벌크 인서트 처리
    try:
        conn = get_db_conn()
        cur = conn.cursor()
        for record, embedding in results:
            if not embedding:
                continue
            content = f"{record.category} {record.merchantName} {record.amount}원"
            cur.execute("""
                INSERT INTO spending_embeddings (user_id, category, merchant_name, amount, embedding, content, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s::vector, %s, NOW(), NOW())
            """, (request.userId, record.category, record.merchantName, record.amount, str(embedding), content))
        conn.commit()
        conn.close()
        logger.info(f"[SpendingAnalysisAgent] 임베딩 저장 완료: {len(results)}건")
    except Exception as e:
        logger.error(f"[SpendingAnalysisAgent] 벌크 임베딩 저장 실패: {e}")

    logger.info(f"[SpendingAnalysisAgent] 완료: {list(category_expenses.keys())}")
    return SpendingAnalysisResponse(
        categoryExpenses=category_expenses,
        totalExpenses=total,
        dailyAvailableAmount=daily,
        topCategory=top
    )

@app.get("/health")
def health():
    return {"status": "ok", "agent": "SpendingAnalysisAgent", "port": 8002}

class AutoOverageRequest(BaseModel):
    userId: int
    weeklyExpenses: dict[str, int]
    weeklyAvgExpenses: dict[str, int]

class OverageCategory(BaseModel):
    category: str
    thisWeek: int
    avgWeek: int
    overageAmount: int
    overageRate: float

class AutoOverageResponse(BaseModel):
    hasOverage: bool
    overageCategories: list[OverageCategory]
    proposalReason: str

@app.post("/agent/detect-auto-overage", response_model=AutoOverageResponse)
async def detect_auto_overage(request: AutoOverageRequest):
    overage_categories = []
    for category, this_week in request.weeklyExpenses.items():
        avg = request.weeklyAvgExpenses.get(category, 0)
        if avg <= 0:
            continue
        overage_rate = (this_week - avg) / avg * 100
        if overage_rate > 25:
            overage_categories.append(OverageCategory(
                category=category,
                thisWeek=this_week,
                avgWeek=avg,
                overageAmount=this_week - avg,
                overageRate=round(overage_rate, 1)
            ))

    if not overage_categories:
        return AutoOverageResponse(
            hasOverage=False, overageCategories=[], proposalReason=""
        )

    # 정렬하여 가장 초과율이 높은 카테고리를 대표 제안 사유로 제공
    overage_categories.sort(key=lambda x: x.overageRate, reverse=True)
    top = overage_categories[0]
    reason = (f"이번 주 {top.category} 지출이 "
              f"평균보다 {top.overageAmount:,}원 많았어요. "
              f"미션으로 밸런스를 맞춰볼까요?")

    return AutoOverageResponse(
        hasOverage=True,
        overageCategories=overage_categories,
        proposalReason=reason
    )
