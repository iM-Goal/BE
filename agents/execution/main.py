from fastapi import FastAPI
from pydantic import BaseModel
import os
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="iM AgentiX — ExecutionAgent")

class ExecutionRequest(BaseModel):
    userId: int
    missionId: int
    category: str | None = None
    limitAmount: int
    durationDays: int
    totalSpend: int  # 미션 기간 동안 해당 카테고리 총 소비

class ExecutionResponse(BaseModel):
    success: bool
    depositReturn: bool
    message: str
    incomeQuintile: int | None = None
    thresholdUsed: float | None = None

@app.post("/agent/execute", response_model=ExecutionResponse)
async def execute_mission(request: ExecutionRequest):
    logger.info(f"[ExecutionAgent] userId={request.userId}, missionId={request.missionId}")

    if request.durationDays <= 0:
        return ExecutionResponse(success=False, depositReturn=False, message="유효하지 않은 미션 기간")

    daily_avg = request.totalSpend / request.durationDays
    if daily_avg <= request.limitAmount:
        logger.info(f"[ExecutionAgent] 미션 성공: 일평균 {daily_avg}원 <= 한도 {request.limitAmount}원")
        return ExecutionResponse(
            success=True,
            depositReturn=True,
            message=f"미션 성공! 일평균 {daily_avg:,.0f}원으로 한도 {request.limitAmount:,}원을 지켰어요.",
            incomeQuintile=3,      # 기본값 제공
            thresholdUsed=0.25     # 기본값 제공
        )
    else:
        logger.info(f"[ExecutionAgent] 미션 실패: 일평균 {daily_avg}원 > 한도 {request.limitAmount}원")
        return ExecutionResponse(
            success=False,
            depositReturn=False,
            message=f"미션 실패. 일평균 {daily_avg:,.0f}원으로 한도 {request.limitAmount:,}원을 초과했어요.",
            incomeQuintile=3,      # 기본값 제공
            thresholdUsed=0.25     # 기본값 제공
        )

@app.get("/health")
def health():
    return {"status": "ok", "agent": "ExecutionAgent", "port": 8004}
