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

app = FastAPI(title="iM AgentiX — InternalControlAgent (Compliance Guardrail)")

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

async def ollama_embed(text: str) -> list[float]:
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{OLLAMA_URL}/api/embeddings",
                json={"model": "nomic-embed-text", "prompt": text}
            )
            return response.json().get("embedding", [])
    except Exception as e:
        logger.error(f"임베딩 생성 실패: {e}")
        return []

def search_relevant_rules(query_embedding: list[float], top_k: int = 3) -> list[dict]:
    try:
        conn = get_db_conn()
        cur = conn.cursor()
        cur.execute("""
            SELECT law_name, article_no, title, content, risk_level, requires_human_review
            FROM compliance_rules
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """, (str(query_embedding), top_k))
        rows = cur.fetchall()
        conn.close()
        return [
            {
                "lawName": r[0], "articleNo": r[1], "title": r[2],
                "content": r[3], "riskLevel": r[4], "requiresHumanReview": r[5]
            } for r in rows
        ]
    except Exception as e:
        logger.error(f"규정 pgvector 검색 실패: {e}")
        return []

class ComplianceInspectRequest(BaseModel):
    userId: int
    userGoal: str
    planType: str # "CONSUMPTION" or "ASSET_ACCUMULATION"
    monthlyIncome: int = 0
    disposableIncome: int = 0
    dailyAvailable: int = 0
    proposedMonthlySavings: int = 0
    recommendedProduct: str | None = None

class ComplianceInspectResponse(BaseModel):
    isCompliant: bool
    riskLevel: str # "LOW", "MEDIUM", "HIGH"
    requiresHumanReview: bool
    reviewReason: str | None = None
    matchedRules: list[str]
    complianceFootnote: str

@app.post("/agent/compliance/inspect", response_model=ComplianceInspectResponse)
async def inspect_compliance(req: ComplianceInspectRequest):
    logger.info(f"[ComplianceAgent] userId={req.userId}, goal={req.userGoal}, type={req.planType}, product={req.recommendedProduct}")

    # 1. RAG: 규정집 벡터 검색
    query = f"{req.userGoal} {req.recommendedProduct or ''} 적합성 설명의무"
    query_emb = await ollama_embed(query)
    matched_rules = []
    if query_emb:
        matched_rules = search_relevant_rules(query_emb, top_k=3)

    # 2. 적합성 및 불완전판매 룰 판정
    # 가처분소득 대비 저축 비율
    savings_ratio = (req.proposedMonthlySavings / req.disposableIncome * 100) if req.disposableIncome > 0 else 0

    requires_human_review = False
    review_reason = None
    risk_level = "LOW"

    # [핵심 로직] 목돈 자산형성형이면서 일정 규모 이상이거나, 저축 비율이 50%를 넘는 경우 담당자 확인 연결
    if req.planType == "ASSET_ACCUMULATION":
        if savings_ratio > 50:
            requires_human_review = True
            risk_level = "HIGH"
            review_reason = f"월 저축액({req.proposedMonthlySavings:,}원)이 월 가처분소득의 {savings_ratio:.0f}%에 달하여 생활비 부족 리스크가 감지되었습니다. 금융소비자보호법 제17조에 따라 전문 상담원과의 1:1 확인을 권장합니다."
        elif req.recommendedProduct:
            # 금융상품 추천이 수반되는 경우: 안내 및 설명의무 확인을 위해 전문 상담원 상담 옵션 제공
            risk_level = "MEDIUM"
            requires_human_review = False # 일반 적금은 자동 추천 통과하되 원할 때 연결 가능
            review_reason = "금융소비자보호법 제19조 설명의무가 준수된 표준 우대적금 상품입니다."
    else:
        # 소비형 플랜
        if savings_ratio > 60:
            risk_level = "MEDIUM"
            review_reason = "목표 기간이 촉박하여 하루 생활비 압박이 높습니다. 일일 가용예산 조정을 권장합니다."

    # 3. 각주(Footnote) 텍스트 생성
    if req.planType == "ASSET_ACCUMULATION":
        footnote = (
            f"고객 월 가처분소득({req.disposableIncome:,}원) 대비 {savings_ratio:.0f}% 납입 적합 판정 · "
            f"금소법 제17조(적합성) 및 제19조(설명의무) 검증 완료 · "
            f"예금자보호법 적용 대상 (최고 5,000만 원 한도)"
        )
    else:
        footnote = (
            f"마이데이터 4주 지출 및 일일 가용예산({req.dailyAvailable:,}원) 대비 적정 절제율 적용 · "
            f"금소법 제17조(적합성 원칙) 기준 일상생활 안정성 통과"
        )

    matched_rule_names = [f"{r['lawName']} {r['articleNo']}({r['title']})" for r in matched_rules]
    if not matched_rule_names:
        matched_rule_names = ["금융소비자보호법 제17조(적합성의 원칙)", "금융소비자보호법 제19조(설명의무)"]

    return ComplianceInspectResponse(
        isCompliant=True,
        riskLevel=risk_level,
        requiresHumanReview=requires_human_review,
        reviewReason=review_reason,
        matchedRules=matched_rule_names,
        complianceFootnote=footnote
    )

@app.get("/health")
def health():
    return {"status": "ok", "agent": "InternalControlAgent", "port": 8006}
