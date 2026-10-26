"""
simulation/generate_cohort_dataset.py
iM GOAL 효과 검증 시뮬레이션을 위한 1,000명 가상 코호트 데이터셋 생성기
스펙: iM GOAL 효과 검증 시뮬레이션 상세 구현 스펙 2절 및 4절
"""

import os
import pandas as pd
import numpy as np
from scipy import stats

RAW_DIR = "data/raw"
PROCESSED_DIR = "data/processed"
OUTPUT_PATH = os.path.join(PROCESSED_DIR, "cohort_1000.csv")

# 상수 정의 (스펙 명시값 준수)
DEFAULT_20S_AVG_INCOME = 2450000  # 고용노동부 워크넷 29세 이하 평균임금 앵커 (기존 262.6만 원은 소비지출 수치였으므로 소득 통계로 정정)
DEFAULT_20S_AVG_CONSUMPTION = 2626000  # 통계청 가계동향조사 39세 이하 평균 소비지출 앵커
DISCRETIONARY_BUDGET_ANCHOR = 400000  # 한국은행/카드사 추정범위 (재량지출 앵커)
TOTAL_CARD_USAGE_ANCHOR = 1000000     # 한국은행/카드사 추정범위 (카드이용액 앵커)
TARGET_FUND_ANCHOR = 10000000         # 서비스 기획 고정 가정치 (1,000만 원)
TARGET_MONTHS_ANCHOR = 10             # 서비스 기획 고정 가정치 (10개월)
VULNERABLE_UTIL_THRESHOLD = 0.5       # 취약군 임계값: utilization >= 0.5

# 한국 20대 기준 신용한도 분포 파라미터 (가정치: 평균 300만 원, 표준편차 150만 원, 실측 아님, 스펙 주석 필수)
KR_20S_LIMIT_MEAN = 3000000
KR_20S_LIMIT_STD = 1500000
KR_20S_LIMIT_MIN = 500000


def calculate_beta_params(anchors=(0.284, 0.323, 0.450)):
    """
    Ashraf(2006), Karlan(2012), Anett John(2018) 3대 실증 연구 앵커 기반
    모멘트법을 통한 알파(미션 성공확률) 베타분포 파라미터 산출
    """
    mean = float(np.mean(anchors))
    var = float(np.var(anchors, ddof=1))
    common = mean * (1 - mean) / var - 1
    alpha_param = mean * common
    beta_param = (1 - mean) * common
    return alpha_param, beta_param


def generate_cohort(n_agents=1000, random_seed=42, output_path=OUTPUT_PATH):
    np.random.seed(random_seed)
    if output_path:
        os.makedirs(os.path.dirname(output_path), exist_ok=True)

    # 1. 원천 데이터 로드
    nemotron_path = os.path.join(RAW_DIR, "nemotron_personas_korea.csv")
    wage_path = os.path.join(RAW_DIR, "worknet_wage_table.csv")
    uci_path = os.path.join(RAW_DIR, "uci_credit_card_clients.csv")

    if not os.path.exists(nemotron_path) or not os.path.exists(wage_path) or not os.path.exists(uci_path):
        raise FileNotFoundError("원천 데이터 파일(raw)이 누락되었습니다. scripts/prepare_raw_data.py를 먼저 실행하세요.")

    df_nemotron = pd.read_csv(nemotron_path)
    df_wage = pd.read_csv(wage_path)
    df_uci = pd.read_csv(uci_path)

    # STEP 1: 인구통계·서사 이식 (20~29세 조건 필터링 후 1,000명 무작위 추출)
    df_20s = df_nemotron[(df_nemotron["age"] >= 20) & (df_nemotron["age"] <= 29)]
    if len(df_20s) < n_agents:
        cohort_base = df_20s.sample(n=n_agents, replace=True, random_state=random_seed).reset_index(drop=True)
    else:
        cohort_base = df_20s.sample(n=n_agents, replace=False, random_state=random_seed).reset_index(drop=True)

    # STEP 2: 소득 이식 (occupation -> 워크넷 직업별 평균임금 매칭, 매칭 실패 시 전체 20대 평균값 대체)
    wage_dict = dict(zip(df_wage["occupation"], df_wage["avg_monthly_income"]))
    income_list = []
    for occ in cohort_base["occupation"]:
        inc = wage_dict.get(occ, DEFAULT_20S_AVG_INCOME)
        income_list.append(int(inc))

    # STEP 3: 신용/부채 프로필 이식 (UCI 데이터에서 부트스트랩 복원추출)
    # 백분위(Percentile)를 한국 20대 기준 신용한도 분포(평균 300만, 편차 150만 원, 최소 50만 원 가정치)에 재매핑
    uci_bootstrap = df_uci.sample(n=n_agents, replace=True, random_state=random_seed).reset_index(drop=True)
    uci_limit_ranks = uci_bootstrap["LIMIT_BAL"].rank(pct=True).clip(0.01, 0.99)

    # scipy.stats.norm.ppf로 한국 20대 신용한도 재매핑
    korean_limits = stats.norm.ppf(uci_limit_ranks, loc=KR_20S_LIMIT_MEAN, scale=KR_20S_LIMIT_STD)
    korean_limits = np.maximum(korean_limits, KR_20S_LIMIT_MIN).astype(int)

    # utilization 및 current_debt 산출
    utilizations = uci_bootstrap["utilization"].clip(0.01, 0.99).values
    current_debts = (korean_limits * utilizations).astype(int)
    final_utilizations = np.round(current_debts / korean_limits, 4)

    # STEP 4: 소비 baseline 스케일링 (재량지출 40만 원 ±10% 정규 노이즈, 총 카드이용 100만 원 고정)
    discretionary_noise = np.random.normal(loc=1.0, scale=0.05, size=n_agents).clip(0.90, 1.10)
    discretionary_budgets = (DISCRETIONARY_BUDGET_ANCHOR * discretionary_noise).astype(int)
    total_card_usages = [TOTAL_CARD_USAGE_ANCHOR] * n_agents

    # STEP 5: 미션 성공확률(alpha) 부여 (베타분포 모멘트법 캘리브레이션)
    alpha_param, beta_param = calculate_beta_params()
    agent_alphas = np.random.beta(alpha_param, beta_param, size=n_agents)
    agent_alphas = np.round(agent_alphas, 4)

    # 세그먼트 분류 (utilization >= 0.5 -> "취약", 아니면 "일반")
    segments = ["취약" if u >= VULNERABLE_UTIL_THRESHOLD else "일반" for u in final_utilizations]

    # 데이터프레임 구성
    cohort_df = pd.DataFrame({
        "user_id": [str(i) for i in range(n_agents)],
        "age": cohort_base["age"].astype(int),
        "occupation": cohort_base["occupation"],
        "district": cohort_base["district"],
        "income": income_list,
        "discretionary_budget": discretionary_budgets,
        "total_card_usage": total_card_usages,
        "credit_limit": korean_limits,
        "current_debt": current_debts,
        "utilization": final_utilizations,
        "alpha": agent_alphas,
        "segment": segments,
        "target_fund": [TARGET_FUND_ANCHOR] * n_agents,
        "target_months": [TARGET_MONTHS_ANCHOR] * n_agents
    })

    # 저장
    if output_path:
        cohort_df.to_csv(output_path, index=False, encoding="utf-8")
    return cohort_df


if __name__ == "__main__":
    df = generate_cohort(n_agents=1000)
    print(f"[OK] {OUTPUT_PATH} 생성 완료: 총 {len(df)}명")
    print("\n--- [cohort_1000.csv 헤드 5줄] ---")
    print(df.head(5).to_string())
    print("\n--- [각 컬럼 기술통계 (mean / std / min / max)] ---")
    numeric_cols = ["age", "income", "discretionary_budget", "total_card_usage", "credit_limit", "current_debt", "utilization", "alpha", "target_fund", "target_months"]
    print(df[numeric_cols].describe().loc[["mean", "std", "min", "max"]].to_string())
    print("\n--- [세그먼트 분포] ---")
    print(df["segment"].value_counts().to_string())
