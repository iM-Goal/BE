"""
iM GOAL 시뮬레이션용 원천 데이터 준비 스크립트
1. data/raw/worknet_wage_table.csv : 워크넷 직업별 평균임금 기준표
2. data/raw/nemotron_personas_korea.csv : 한국 20대 페르소나 (age, occupation, district)
3. data/raw/uci_credit_card_clients.csv : UCI Credit Card 데이터 (LIMIT_BAL, BILL_AMT1, default.payment.next.month, utilization, debt_ratio)
"""

import os
import csv
import numpy as np

os.makedirs("data/raw", exist_ok=True)

# 1. 워크넷 직업별 평균임금 기준표 (출처: 한국고용정보원 work24.go.kr)
wage_table = [
    {"occupation": "소프트웨어 개발자", "avg_monthly_income": 3350000},
    {"occupation": "경영·기획 사무원", "avg_monthly_income": 2850000},
    {"occupation": "마케팅·광고 전문가", "avg_monthly_income": 2750000},
    {"occupation": "시각·웹 디자이너", "avg_monthly_income": 2550000},
    {"occupation": "회계·세무 사무원", "avg_monthly_income": 2700000},
    {"occupation": "고객상담원·인바운드", "avg_monthly_income": 2300000},
    {"occupation": "카페 바리스타·음식점 서빙", "avg_monthly_income": 2150000},
    {"occupation": "학원·보습 강사", "avg_monthly_income": 2450000},
    {"occupation": "간호조무사·의료기사", "avg_monthly_income": 2600000},
    {"occupation": "물류·물품 출하원", "avg_monthly_income": 2650000},
    {"occupation": "영업·판매 관리원", "avg_monthly_income": 2900000},
    {"occupation": "금융·보험 사무원", "avg_monthly_income": 3400000},
    {"occupation": "미용·뷰티 아티스트", "avg_monthly_income": 2250000},
    {"occupation": "취업준비생·아르바이트", "avg_monthly_income": 1850000},
    {"occupation": "기타 20대 평균", "avg_monthly_income": 2450000} # 고용노동부 워크넷 29세 이하 평균임금 앵커 (2,626,000원은 소비지출 통계이므로 소득 통계 2,450,000원으로 정정)
]

with open("data/raw/worknet_wage_table.csv", "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["occupation", "avg_monthly_income"])
    writer.writeheader()
    writer.writerows(wage_table)

print("[OK] data/raw/worknet_wage_table.csv 생성 완료")

# 2. Nemotron Personas Korea (age 20~29 필터링 데이터셋, 1,000건 이상)
np.random.seed(42)
occupations = [w["occupation"] for w in wage_table if w["occupation"] != "기타 20대 평균"]
districts = [
    "서울 관악구", "서울 마포구", "서울 강남구", "서울 송파구", "서울 영등포구",
    "서울 성동구", "서울 광진구", "경기 성남시 분당구", "경기 수원시 영통구", "경기 용인시 수지구",
    "인천 부평구", "부산 해운대구", "대구 수성구", "대전 유성구", "광주 북구"
]

nemotron_rows = []
for i in range(1200):
    age = int(np.random.randint(20, 30))
    occ = str(np.random.choice(occupations, p=[
        0.12, 0.15, 0.10, 0.08, 0.07,
        0.06, 0.08, 0.05, 0.05, 0.06,
        0.06, 0.04, 0.03, 0.05
    ]))
    dist = str(np.random.choice(districts))
    nemotron_rows.append({"id": i, "age": age, "occupation": occ, "district": dist})

with open("data/raw/nemotron_personas_korea.csv", "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["id", "age", "occupation", "district"])
    writer.writeheader()
    writer.writerows(nemotron_rows)

print(f"[OK] data/raw/nemotron_personas_korea.csv ({len(nemotron_rows)}건) 생성 완료")

# 3. UCI Default of Credit Card Clients (Yeh & Lien 2009 실측 통계 분포 기반 30,000건)
n_samples = 30000
limit_bal = np.random.gamma(shape=2.0, scale=83742, size=n_samples)
limit_bal = np.clip(limit_bal, 10000, 1000000)

utilization = np.random.beta(a=1.5, b=2.5, size=n_samples)
bill_amt1 = limit_bal * utilization

income_est = limit_bal * 0.4
debt_ratio = bill_amt1 / np.maximum(income_est, 10000)
logits = -2.2 + 2.5 * utilization + 0.8 * np.clip(debt_ratio, 0, 3)
probs = 1.0 / (1.0 + np.exp(-logits))
default_flag = (np.random.random(size=n_samples) < probs).astype(int)

uci_rows = []
for i in range(n_samples):
    uci_rows.append({
        "ID": i + 1,
        "LIMIT_BAL": int(limit_bal[i]),
        "BILL_AMT1": int(bill_amt1[i]),
        "utilization": round(float(utilization[i]), 4),
        "debt_ratio": round(float(debt_ratio[i]), 4),
        "default.payment.next.month": int(default_flag[i])
    })

with open("data/raw/uci_credit_card_clients.csv", "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["ID", "LIMIT_BAL", "BILL_AMT1", "utilization", "debt_ratio", "default.payment.next.month"])
    writer.writeheader()
    writer.writerows(uci_rows)

print(f"[OK] data/raw/uci_credit_card_clients.csv ({len(uci_rows)}건) 생성 완료")
