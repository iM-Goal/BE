from fastapi import FastAPI
from pydantic import BaseModel
from datetime import date, timedelta
import random

app = FastAPI(title="iM AgentiX Mock API")

# 더미 소비 내역 데이터
MOCK_SPENDING = {
    "categories": ["카페", "식비", "교통", "편의점", "배달음식"],
    "merchants": {
        "카페": ["스타벅스", "메가커피", "이디야"],
        "식비": ["맥도날드", "롯데리아", "버거킹"],
        "교통": ["지하철", "버스", "택시"],
        "편의점": ["CU", "GS25", "세븐일레븐"],
        "배달음식": ["배달의민족", "쿠팡이츠", "요기요"]
    }
}

class TransferRequest(BaseModel):
    userId: int
    goalId: int
    amount: int

class SpendingItem(BaseModel):
    category: str
    amount: int
    merchantName: str
    date: str

@app.get("/mock/spending/{user_id}")
def get_spending(user_id: int):
    """더미 소비 내역 반환 (최근 30일)"""
    spending = []
    today = date.today()
    
    for i in range(30):
        target_date = today - timedelta(days=i)
        num_transactions = random.randint(1, 4)
        
        for _ in range(num_transactions):
            category = random.choice(MOCK_SPENDING["categories"])
            merchant = random.choice(MOCK_SPENDING["merchants"][category])
            
            amount_ranges = {
                "카페": (3000, 8000),
                "식비": (5000, 15000),
                "교통": (1250, 3000),
                "편의점": (1000, 5000),
                "배달음식": (10000, 30000)
            }
            min_amt, max_amt = amount_ranges[category]
            amount = random.randint(min_amt // 100, max_amt // 100) * 100
            
            spending.append({
                "category": category,
                "amount": amount,
                "merchantName": merchant,
                "date": str(target_date)
            })
    
    return {"userId": user_id, "spending": spending}

@app.post("/mock/transfer")
def transfer(request: TransferRequest):
    """Mock 계좌 이체"""
    print(f"[TRANSFER] userId={request.userId}, goalId={request.goalId}, amount={request.amount}원")
    return {"success": True, "message": f"{request.amount}원이 목표 저축계좌로 이체되었습니다."}

@app.get("/mock/withdrawal-conditions/templates")
def get_templates():
    """출금 조건 템플릿 목록"""
    return {
        "templates": [
            {"id": 1, "type": "DAY_OF_WEEK", "description": "주말 출금 불가", "condition": {"days": ["SATURDAY", "SUNDAY"]}},
            {"id": 2, "type": "AMOUNT_LIMIT", "description": "1회 1만원 이상 출금 불가", "condition": {"maxAmount": 10000}},
            {"id": 3, "type": "ACHIEVEMENT_RATE", "description": "달성률 50% 전 잠금", "condition": {"minRate": 50.0}},
            {"id": 4, "type": "MISSION_STREAK", "description": "미션 연속 3일 실패 시 잠금", "condition": {"failStreak": 3}},
            {"id": 5, "type": "TIME_RESTRICTION", "description": "자정~오전 6시 출금 불가", "condition": {"startHour": 0, "endHour": 6}}
        ]
    }

@app.post("/mock/withdrawal-conditions/recommend")
def recommend_conditions(body: dict):
    """소비 패턴 기반 출금 조건 추천 (Mock)"""
    return {
        "recommended": [
            {"id": 1, "type": "DAY_OF_WEEK", "description": "주말 출금 불가"},
            {"id": 3, "type": "ACHIEVEMENT_RATE", "description": "달성률 50% 전 잠금"}
        ],
        "reason": "주말 소비 패턴이 높아 주말 출금 제한을 추천합니다."
    }

class SmartContractRequest(BaseModel):
    userId: int
    goalId: int
    conditions: list

class SmartContractResponse(BaseModel):
    contractId: str
    status: str
    conditions: list

@app.post("/mock/blockchain/contract/sign")
def sign_contract(request: SmartContractRequest):
    """스마트컨트랙트 체결 Mock"""
    import uuid
    contract_id = str(uuid.uuid4())[:8]
    print(f"[BLOCKCHAIN] 스마트컨트랙트 체결: userId={request.userId}, goalId={request.goalId}, contractId={contract_id}")
    return {
        "contractId": contract_id,
        "status": "SIGNED",
        "conditions": request.conditions,
        "message": f"출금 조건 {len(request.conditions)}개가 스마트컨트랙트에 체결되었습니다."
    }

@app.delete("/mock/blockchain/contract/{contract_id}")
def release_contract(contract_id: str):
    """스마트컨트랙트 해제 Mock (Buy-out 시)"""
    print(f"[BLOCKCHAIN] 스마트컨트랙트 해제: contractId={contract_id}")
    return {
        "contractId": contract_id,
        "status": "RELEASED",
        "message": "출금 조건이 해제되었습니다. 목표 달성을 축하합니다!"
    }


# iMKRW 토큰 발행 (원화 → iMKRW)
class TokenMintRequest(BaseModel):
    userId: int
    amount: int  # 원화 금액

@app.post("/mock/imkrw/mint")
def mint_token(request: TokenMintRequest):
    """원화를 iMKRW 스테이블코인으로 발행 (1:1 페깅)"""
    token_amount = request.amount  # 1원 = 1 iMKRW
    print(f"[iMKRW] 토큰 발행: userId={request.userId}, amount={request.amount}원 → {token_amount} iMKRW")
    return {
        "success": True,
        "userId": request.userId,
        "mintedAmount": token_amount,
        "currency": "iMKRW",
        "txHash": f"0x{request.userId:04x}{request.amount:08x}",
        "message": f"{token_amount} iMKRW가 발행되었습니다."
    }

# iMKRW 토큰 소각 (iMKRW → 원화)
class TokenBurnRequest(BaseModel):
    userId: int
    amount: int  # iMKRW 금액

@app.post("/mock/imkrw/burn")
def burn_token(request: TokenBurnRequest):
    """iMKRW 스테이블코인을 원화로 소각"""
    won_amount = request.amount  # 1 iMKRW = 1원
    print(f"[iMKRW] 토큰 소각: userId={request.userId}, amount={request.amount} iMKRW → {won_amount}원")
    return {
        "success": True,
        "userId": request.userId,
        "burnedAmount": request.amount,
        "wonAmount": won_amount,
        "currency": "KRW",
        "txHash": f"0xburn{request.userId:04x}{request.amount:08x}",
        "message": f"{request.amount} iMKRW가 소각되어 {won_amount}원이 출금됩니다."
    }

# Kaia 스마트컨트랙트 잠금 (저축 시 토큰 잠금)
class TokenLockRequest(BaseModel):
    userId: int
    goalId: int
    amount: int
    conditions: list

@app.post("/mock/kaia/lock")
def lock_token(request: TokenLockRequest):
    """iMKRW를 Kaia 스마트컨트랙트에 잠금"""
    import uuid
    lock_id = str(uuid.uuid4())[:8]
    print(f"[Kaia] 토큰 잠금: userId={request.userId}, goalId={request.goalId}, amount={request.amount} iMKRW")
    return {
        "success": True,
        "lockId": lock_id,
        "userId": request.userId,
        "goalId": request.goalId,
        "lockedAmount": request.amount,
        "conditions": request.conditions,
        "message": f"{request.amount} iMKRW가 스마트컨트랙트에 잠겼습니다."
    }

# Kaia 스마트컨트랙트 잠금 해제 (Buy-out 시)
@app.post("/mock/kaia/unlock/{lock_id}")
def unlock_token(lock_id: str, body: dict = {}):
    """Kaia 스마트컨트랙트 잠금 해제"""
    print(f"[Kaia] 토큰 잠금 해제: lockId={lock_id}")
    return {
        "success": True,
        "lockId": lock_id,
        "status": "UNLOCKED",
        "message": "스마트컨트랙트 잠금이 해제되었습니다."
    }

# DAGAON 지갑 출금
class WalletWithdrawRequest(BaseModel):
    userId: int
    amount: int
    purpose: str  # 출금 목적 (ex: "나이키 에어맥스 구매")

@app.post("/mock/dagaon/withdraw")
def wallet_withdraw(request: WalletWithdrawRequest):
    """DAGAON 지갑으로 출금"""
    print(f"[DAGAON] 출금: userId={request.userId}, amount={request.amount}원, purpose={request.purpose}")
    return {
        "success": True,
        "userId": request.userId,
        "withdrawAmount": request.amount,
        "purpose": request.purpose,
        "walletAddress": f"0xDAGAON{request.userId:04x}",
        "message": f"{request.amount}원이 DAGAON 지갑으로 출금되었습니다. 쇼핑을 즐기세요!"
    }

# iMKRW 잔액 조회
@app.get("/mock/imkrw/balance/{user_id}")
def get_balance(user_id: int):
    """사용자 iMKRW 잔액 조회 (Mock - 목표 저축액 기반)"""
    return {
        "userId": user_id,
        "balance": 50000,  # Mock 잔액
        "currency": "iMKRW",
        "lockedAmount": 30000,  # 스마트컨트랙트에 잠긴 금액
        "availableAmount": 20000  # 사용 가능 금액
    }

# 보증금 잠금 (미션 수락 시)
@app.post("/mock/kaia/lock-deposit")
def lock_deposit(body: dict):
    import uuid
    lock_id = str(uuid.uuid4())[:8]
    return {
        "success": True,
        "lockId": lock_id,
        "txHash": f"0xlock{lock_id}",
        "lockedAmount": body.get("amount", 0)
    }

# 보증금 해제 (미션 성공 시)
@app.post("/mock/kaia/release-deposit/{lock_id}")
def release_deposit(lock_id: str):
    return {
        "success": True,
        "lockId": lock_id,
        "txHash": f"0xrelease{lock_id}",
        "status": "RELEASED"
    }

# 보증금 출금 (마찰 절차 후)
@app.post("/mock/kaia/withdraw-deposit/{lock_id}")
def withdraw_deposit(lock_id: str, body: dict = {}):
    return {
        "success": True,
        "lockId": lock_id,
        "txHash": f"0xwithdraw{lock_id}",
        "reason": body.get("reason", "UNKNOWN"),
        "status": "WITHDRAWN"
    }

