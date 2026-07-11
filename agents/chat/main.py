from fastapi import FastAPI
from pydantic import BaseModel
import httpx
import json
import os
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="iM AgentiX — ChatAgent")

OLLAMA_URL = os.getenv("OLLAMA_BASE_URL", "http://host.docker.internal:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:1.5b")


# ── 요청 / 응답 모델 ─────────────────────────────────────────

class FinancialInfo(BaseModel):
    monthlyIncome: int = 0
    disposableIncome: int = 0
    dailyAvailable: int = 0
    incomeQuintile: int = 3

class GoalInfo(BaseModel):
    slotType: str
    itemName: str
    targetAmount: int
    currentAmount: int
    achievementRate: float
    estimatedCompletionDate: str | None = None

class MissionInfo(BaseModel):
    status: str
    category: str | None = None
    limitAmount: int | None = None
    description: str | None = None

class ChatContext(BaseModel):
    financialInfo: FinancialInfo | None = None
    goals: list[GoalInfo] = []
    missions: list[MissionInfo] = []
    totalDeposit: int = 0
    categoryExpenses: dict[str, int] = {}

class ChatRequest(BaseModel):
    userId: int
    message: str
    context: ChatContext | None = None

class ChatResponse(BaseModel):
    answer: str


# ── Ollama 호출 ──────────────────────────────────────────────

async def ollama_generate(prompt: str, system: str) -> str:
    full_prompt = f"[SYSTEM]\n{system}\n\n[USER]\n{prompt}"
    async with httpx.AsyncClient(timeout=180.0) as client:
        response = await client.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": OLLAMA_MODEL,
                "prompt": full_prompt,
                "stream": False
            }
        )
        return response.json().get("response", "").strip()


# ── 컨텍스트 → 프롬프트 변환 ─────────────────────────────────

def build_context_text(ctx: ChatContext | None) -> str:
    if not ctx:
        return "사용자 데이터 없음"

    lines = []

    # 재정 정보
    fi = ctx.financialInfo
    if fi:
        lines.append(f"[재정 정보]")
        lines.append(f"- 월 소득: {fi.monthlyIncome:,}원")
        lines.append(f"- 처분가능소득: {fi.disposableIncome:,}원")
        lines.append(f"- 일일 가용금액: {fi.dailyAvailable:,}원")
        lines.append(f"- 소득분위: {fi.incomeQuintile}분위")

    # 목표
    if ctx.goals:
        lines.append("\n[목표 현황]")
        for g in ctx.goals:
            lines.append(
                f"- [{g.slotType}] {g.itemName}: "
                f"{g.currentAmount:,}원 / {g.targetAmount:,}원 "
                f"(달성률 {g.achievementRate:.1f}%)"
                + (f", 예상완료: {g.estimatedCompletionDate}" if g.estimatedCompletionDate else "")
            )

    # 미션
    if ctx.missions:
        lines.append("\n[미션 현황]")
        for m in ctx.missions:
            desc = m.description or ""
            limit = f" 한도 {m.limitAmount:,}원" if m.limitAmount else ""
            lines.append(f"- [{m.status}] {m.category or ''}{limit} {desc}".strip())

    # 자산/보증금
    lines.append(f"\n[보증금 총액] {ctx.totalDeposit:,}원")

    # 카테고리별 지출
    if ctx.categoryExpenses:
        lines.append("\n[카테고리별 지출]")
        for cat, amount in ctx.categoryExpenses.items():
            lines.append(f"- {cat}: {amount:,}원")

    return "\n".join(lines)


SYSTEM_PROMPT = """당신은 iM AgentiX의 개인 금융 상담 AI입니다.
사용자의 실제 소비 데이터, 목표, 미션 현황을 기반으로 따뜻하고 구체적인 조언을 제공합니다.
- 숫자는 반드시 한국 원화 형식(원)으로 표기하세요.
- 사용자 데이터를 적극 활용해 개인화된 답변을 하세요.
- 답변은 3~5문장 이내로 간결하게 작성하세요.
- 불필요한 인사말이나 서두는 생략하고 바로 핵심 답변을 하세요."""


# ── 엔드포인트 ────────────────────────────────────────────────

@app.post("/agent/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    logger.info(f"[ChatAgent] userId={request.userId}, message={request.message[:50]}")

    context_text = build_context_text(request.context)
    prompt = f"""[사용자 데이터]
{context_text}

[사용자 질문]
{request.message}"""

    logger.info(f"[ChatAgent] 프롬프트 길이: {len(prompt)} chars")
    answer = await ollama_generate(prompt, SYSTEM_PROMPT)
    logger.info(f"[ChatAgent] 응답 완료: {answer[:80]}")

    return ChatResponse(answer=answer)


@app.get("/health")
def health():
    return {"status": "ok", "agent": "ChatAgent", "port": 8005}
