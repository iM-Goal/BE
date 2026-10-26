"""
simulation/financial_impact_models.py
iM GOAL 효과 검증 시뮬레이션을 위한 행동 규칙 및 금융공학 실측 모델
스펙: iM GOAL 효과 검증 시뮬레이션 상세 구현 스펙 3절, 5절, 6절
"""

import os
import pandas as pd
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score

# 3절. 미션 목표금액 시나리오 (정확한 공식)
MISSION_SIZE_SCENARIOS = {
    "conservative": 0.054,              # NYU Stern(Sung K. Lee) 앵커
    "optimistic_general": 0.09,         # CUHK 앵커(일반군)
    "optimistic_vulnerable": 0.30,      # CUHK 앵커(취약군 상한)
}

# v2 카테고리 분할 예산 (합계: 400,000원 = discretionary_budget과 일치)
CATEGORY_ALLOCATION = {
    "delivery": 150_000,
    "shopping": 100_000,
    "cafe": 80_000,
    "subscription_transport": 70_000,
}
assert sum(CATEGORY_ALLOCATION.values()) == 400_000, "카테고리 예산 합계가 400,000원이어야 합니다."

# 6절. 신용점수 및 B2B 손실 상수 정의
KCB_DEBT_LEVEL_WEIGHT = 0.24  # KCB 일반고객 부채수준 가중치 (출처: 올크레딧·NICE평가정보 공시, KB국민은행)
EAD = 100_000_000_000         # 1,000억 원 (가정치, 대출 채권 익스포저)
LGD = 0.5                     # 50% (국제 신용리스크 실무 통상 가정치, 금감원 표준 아님, 코드 주석 필수)
DEFAULT_20S_AVG_INCOME = 2450000  # 고용노동부 워크넷 29세 이하 평균임금 앵커 (소득 통계 정정)
DEFAULT_20S_AVG_CONSUMPTION = 2626000  # 통계청 39세 이하 가구 평균 소비지출 앵커


def get_mission_target(agent, scenario: str) -> float:
    """
    미션 목표금액 산정 (3절 스펙)
    conservative: 재량지출의 5.4%
    optimistic: 일반군 9%, 취약군 30%
    """
    if scenario == "conservative":
        rate = MISSION_SIZE_SCENARIOS["conservative"]
    else:  # "optimistic"
        segment = getattr(agent, "segment", None) or (agent["segment"] if isinstance(agent, dict) else agent.get("segment"))
        rate = (MISSION_SIZE_SCENARIOS["optimistic_vulnerable"]
                if segment == "취약"
                else MISSION_SIZE_SCENARIOS["optimistic_general"])

    budget = getattr(agent, "discretionary_budget", None)
    if budget is None and isinstance(agent, dict):
        budget = agent.get("discretionary_budget", 400000)
    elif budget is None:
        budget = 400000
    return float(budget) * rate


def simulate_month(agent, scenario: str, pd_model=None) -> bool:
    """
    5절. 미션 시뮬레이션 월 단위 실행 루프
    규칙 A: random() < agent.alpha (미션 성공 여부)
    규칙 B: 성공 시 절감액의 70% 저축, 30% 부채상환 -> utilization 재계산
    """
    alpha = getattr(agent, "alpha", None)
    if alpha is None and isinstance(agent, dict):
        alpha = agent.get("alpha", 0.35)

    success = bool(np.random.random() < alpha)
    if success:
        saved = get_mission_target(agent, scenario)

        # 저축 70% 적립
        if hasattr(agent, "savings"):
            agent.savings += saved * 0.7
        elif isinstance(agent, dict):
            agent["savings"] = agent.get("savings", 0.0) + saved * 0.7

        # 부채상환 30% 반영
        if hasattr(agent, "current_debt"):
            agent.current_debt = max(0.0, agent.current_debt - saved * 0.3)
            agent.utilization = agent.current_debt / agent.credit_limit
        elif isinstance(agent, dict):
            agent["current_debt"] = max(0.0, agent.get("current_debt", 0.0) - saved * 0.3)
            limit = agent.get("credit_limit", 3000000)
            agent["utilization"] = agent["current_debt"] / limit if limit > 0 else 0.0

    return success


def get_category_mission_target(category_budget: float, scenario: str, segment: str) -> float:
    """
    v2 카테고리별 미션 목표금액 산정 (1절 스펙)
    conservative: 카테고리 예산의 5.4%
    optimistic: 일반군 9%, 취약군 30%
    """
    if scenario == "conservative":
        rate = MISSION_SIZE_SCENARIOS["conservative"]  # 0.054
    else:
        rate = (MISSION_SIZE_SCENARIOS["optimistic_vulnerable"]
                if segment == "취약"
                else MISSION_SIZE_SCENARIOS["optimistic_general"])  # 0.30 or 0.09
    return float(category_budget) * rate


# H2 (검증 안 된 가설, 코드에 반영하지 않음 — 주석으로만 남김):
# "미션 크기가 작을수록 개인의 성공확률(alpha)이 높아질 것"이라는 가설이 있으나
# 이를 뒷받침할 실증 데이터가 없어 시뮬레이션에서는 카테고리 크기와 무관하게
# 동일한 alpha를 적용한다. 실제로는 소액 미션의 성공률이 더 높을 가능성이 있어
# 본 시뮬레이션 결과는 과소추정(conservative)일 수 있음을 한계로 명시한다.

def simulate_month_v2(agent, scenario: str, pd_model=None) -> float:
    """
    v2 카테고리 분할 미션 시뮬레이션 월 단위 실행 루프
    - 월 1회 x 4개 카테고리(배달 15만, 쇼핑 10만, 카페 8만, 구독/교통 7만 = 합계 40만)
    - 카테고리별 독립 성공 판정 (alpha 동일 적용)
    - 성공한 카테고리의 절감액 합산
    - 검증용 주석: E[total_saved] = alpha * rate * 400,000 (v1과 수학적으로 동일해야 함)
    - 절감액의 70% 저축, 30% 부채상환
    - 반환값: 당월 총 절감액 (total_saved)
    """
    total_saved = 0.0
    segment = getattr(agent, "segment", None) or (agent["segment"] if isinstance(agent, dict) else agent.get("segment"))
    alpha = getattr(agent, "alpha", None)
    if alpha is None and isinstance(agent, dict):
        alpha = agent.get("alpha", 0.35)

    for category, budget in CATEGORY_ALLOCATION.items():
        success = bool(np.random.random() < alpha)  # 카테고리 무관, 같은 개인 alpha 재사용
        if success:
            total_saved += get_category_mission_target(budget, scenario, segment)

    # 저축 70% 적립
    if hasattr(agent, "savings"):
        agent.savings += total_saved * 0.7
    elif isinstance(agent, dict):
        agent["savings"] = agent.get("savings", 0.0) + total_saved * 0.7

    # 부채상환 30% 반영
    if hasattr(agent, "current_debt"):
        agent.current_debt = max(0.0, agent.current_debt - total_saved * 0.3)
        agent.utilization = agent.current_debt / agent.credit_limit
    elif isinstance(agent, dict):
        agent["current_debt"] = max(0.0, agent.get("current_debt", 0.0) - total_saved * 0.3)
        limit = agent.get("credit_limit", 3000000)
        agent["utilization"] = agent["current_debt"] / limit if limit > 0 else 0.0

    return float(total_saved)


def train_pd_model(uci_csv_path: str = "data/raw/uci_credit_card_clients.csv"):
    """
    6절. PD 모델 학습 (UCI Default of Credit Card Clients)
    독립변수: utilization (한도소진율), debt_ratio (부채비율)
    종속변수: default.payment.next.month (익월 부도/연체 여부)
    """
    if not os.path.exists(uci_csv_path):
        raise FileNotFoundError(f"UCI 데이터셋을 찾을 수 없습니다: {uci_csv_path}")

    df = pd.read_csv(uci_csv_path)
    X = df[["utilization", "debt_ratio"]]
    y = df["default.payment.next.month"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    pd_model = LogisticRegression(random_state=42, max_iter=500)
    pd_model.fit(X_train, y_train)

    y_pred = pd_model.predict(X_test)
    y_prob = pd_model.predict_proba(X_test)[:, 1]

    report = classification_report(y_test, y_pred, digits=4)
    auc = float(roc_auc_score(y_test, y_prob))

    print("=== [UCI Credit Card Clients PD 모델 학습 결과] ===")
    print(f"훈련 데이터 크기: {len(X_train):,}건, 테스트 데이터 크기: {len(X_test):,}건")
    print(f"ROC-AUC Score: {auc:.4f}")
    print("\n[Classification Report]")
    print(report)
    print(f"로지스틱 회귀 계수 (Weights): utilization={pd_model.coef_[0][0]:.4f}, debt_ratio={pd_model.coef_[0][1]:.4f}")
    print(f"절편 (Intercept): {pd_model.intercept_[0]:.4f}")

    return pd_model, report, auc


def predict_pd(agent, pd_model) -> float:
    """
    6절. 에이전트의 현재 상태 기반 연체확률(PD) 예측
    입력 변수: utilization, debt_ratio = current_debt / income
    """
    if isinstance(agent, dict):
        u = float(agent.get("utilization", 0.0))
        debt = float(agent.get("current_debt", 0.0))
        inc = float(agent.get("income", DEFAULT_20S_AVG_INCOME))
    else:
        u = float(getattr(agent, "utilization", 0.0))
        debt = float(getattr(agent, "current_debt", 0.0))
        inc = float(getattr(agent, "income", DEFAULT_20S_AVG_INCOME))

    debt_ratio = debt / inc if inc > 0 else 0.0
    input_df = pd.DataFrame([[u, debt_ratio]], columns=["utilization", "debt_ratio"])
    prob = pd_model.predict_proba(input_df)[0][1]
    return float(prob)


def credit_score_direction(utilization_before: float, utilization_after: float) -> float:
    """
    6절. 신용점수 방향성 산출
    절대 점수(예: +35점)가 아닌 '부채수준 항목 기여도 변화(상대단위)'로만 표기
    공식: delta_u * KCB_DEBT_LEVEL_WEIGHT (0.24)
    """
    delta_u = float(utilization_before) - float(utilization_after)
    return float(delta_u * KCB_DEBT_LEVEL_WEIGHT)


def calc_ead_from_cohort(cohort) -> float:
    """
    3절 스펙: 코호트 실제 신용한도(credit_limit) 합계 산출
    """
    if isinstance(cohort, pd.DataFrame):
        return float(cohort["credit_limit"].sum())
    elif isinstance(cohort, list):
        return float(sum(
            float(a["credit_limit"]) if isinstance(a, dict) else float(getattr(a, "credit_limit", 0.0))
            for a in cohort
        ))
    else:
        raise TypeError(f"지원하지 않는 코호트 형식: {type(cohort)}")


def calc_expected_loss_delta(*args, **kwargs) -> float:
    """
    3절 & 6절. B2B 예상손실 절감액 (바젤 공식: Delta EL = EAD * (pd_before - pd_after) * LGD)
    지원 시그니처:
      1) calc_expected_loss_delta(pd_before, pd_after, ead=EAD, lgd=LGD)  (v1/v2 호환)
      2) calc_expected_loss_delta(ead, pd_before, pd_after, lgd=LGD)        (v3 스펙 시그니처)
    """
    lgd = kwargs.get("lgd", LGD)
    if len(args) == 3:
        if args[0] > 1.0:
            ead, pd_before, pd_after = args[0], args[1], args[2]
        else:
            pd_before, pd_after, ead = args[0], args[1], args[2]
    elif len(args) == 2:
        pd_before, pd_after = args[0], args[1]
        ead = kwargs.get("ead", EAD)
    elif len(args) >= 4:
        if args[0] > 1.0:
            ead, pd_before, pd_after, lgd = args[0], args[1], args[2], args[3]
        else:
            pd_before, pd_after, ead, lgd = args[0], args[1], args[2], args[3]
    else:
        ead = kwargs.get("ead", EAD)
        pd_before = kwargs.get("pd_before", 0.0)
        pd_after = kwargs.get("pd_after", 0.0)

    delta_pd = float(pd_before) - float(pd_after)
    return float(ead * delta_pd * lgd)


# ==============================================================================
# 2절. 이중 목표 구조 (장기 속도 측정 + 단기 확률 측정)
# ==============================================================================

LONG_TERM_TARGET = 10_000_000   # 1,000만원, 10개월 (기존 유지)
LONG_TERM_MONTHS = 10


def calc_short_term_target(scenario: str, mean_alpha: float = 0.3523) -> float:
    """
    3개월 챌린지 목표금액 산출 (기존 확정 상수에서 역산)
    conservative: 400,000 * 0.054 * 0.3523 * 0.7 * 3 ≈ 15,980.33원 (스펙 예시 ≈ 15,900원)
    optimistic (일반군 기준): 400,000 * 0.09 * 0.3523 * 0.7 * 3 ≈ 26,633.88원 (스펙 예시 ≈ 26,500원)
    """
    rate = MISSION_SIZE_SCENARIOS["conservative"] if scenario == "conservative" else MISSION_SIZE_SCENARIOS["optimistic_general"]
    monthly_saving = 400_000 * rate * mean_alpha * 0.7   # 저축 배분 70%만 반영
    return float(monthly_saving * 3)   # 3개월 챌린지


SHORT_TERM_TARGET_CONSERVATIVE = calc_short_term_target("conservative")   # ≈ 15,980원
SHORT_TERM_TARGET_OPTIMISTIC = calc_short_term_target("optimistic")       # ≈ 26,634원


def calc_short_term_achievement_probability(cohort: list, scenario: str, target: float, n_replications: int = 200, pd_model=None, random_seed: int = 42) -> dict:
    """
    200회 몬테카를로 반복 중 3개월 누적 저축액이 target을 넘은 확률 산출
    - 코호트 평균 기준 달성 확률 (cohort_probability): 평균 누적 저축액 >= target인 반복 비율
    - 개인 단위 평균 달성 확률 (individual_probability): target을 달성한 개인 유저 비율의 평균
    - 세그먼트별(일반/취약) 개인 달성률 (segment_individual_probabilities)
    """
    if random_seed is not None:
        np.random.seed(random_seed)

    cohort_success_count = 0
    indiv_success_rates = []
    seg_indiv_rates = {"일반": [], "취약": []}

    for rep in range(n_replications):
        agents = [dict(a, savings=0.0) for a in cohort]
        for month in range(3):
            for agent in agents:
                simulate_month_v2(agent, scenario, pd_model)

        savings = np.array([float(a["savings"]) for a in agents])
        mean_saving = float(np.mean(savings))

        if mean_saving >= target:
            cohort_success_count += 1

        indiv_success_rates.append(float(np.mean(savings >= target)))

        for seg in ["일반", "취약"]:
            seg_s = np.array([float(a["savings"]) for a in agents if a["segment"] == seg])
            if len(seg_s) > 0:
                seg_indiv_rates[seg].append(float(np.mean(seg_s >= target)))

    return {
        "scenario": scenario,
        "target": target,
        "n_replications": n_replications,
        "cohort_probability": float(cohort_success_count / n_replications),
        "individual_probability": float(np.mean(indiv_success_rates)),
        "cohort_success_count": cohort_success_count,
        "segment_individual_probabilities": {
            seg: float(np.mean(rates)) for seg, rates in seg_indiv_rates.items() if len(rates) > 0
        }
    }


# ==============================================================================
# 3절. 리스크등급 전환 지표 (신규)
# ==============================================================================

def risk_tier(pd_value: float) -> str:
    """
    3절 스펙 기준 PD 기반 3단계 리스크 등급 분류
    - pd < 0.20: 안전권
    - 0.20 <= pd < 0.40: 주의군
    - pd >= 0.40: 고위험군
    """
    if pd_value < 0.20:
        return "안전권"
    elif pd_value < 0.40:
        return "주의군"
    else:
        return "고위험군"


def count_tier_transitions(cohort_before: list, cohort_after: list) -> dict:
    """
    시뮬레이션 전/후 코호트 에이전트 간 리스크 등급 이동 건수 집계
    반환: {"고위험군→주의군": N, "주의군→안전권": M, ...}
    """
    transitions = {}
    for before, after in zip(cohort_before, cohort_after):
        pb = before.get("pd", 0.0) if isinstance(before, dict) else getattr(before, "pd", 0.0)
        pa = after.get("pd", 0.0) if isinstance(after, dict) else getattr(after, "pd", 0.0)
        tier_before = risk_tier(pb)
        tier_after = risk_tier(pa)
        if tier_before != tier_after:
            key = f"{tier_before}→{tier_after}"
            transitions[key] = transitions.get(key, 0) + 1
    return transitions


# ==============================================================================
# v3 스펙: 미션 시행 빈도 확정 (주 1회 / 월 4.345회) & 공공 통계 실측 기반 카테고리 단가
# ==============================================================================
CATEGORY_ALLOCATION_V3 = {
    "delivery": 22_000,               # aT 한국농수산식품유통공사 배달외식 실태조사 1인가구 1회 결제단가
    "shopping": 14_000,               # 통계청 29세 이하 1인가구 의류·신발 지출(월 11.2만) 주간 절제액
    "cafe": 9_000,                   # KB국민카드 20대 커피전문점 건당 실측 결제액(8,700원 반올림)
    "subscription_transport": 7_500, # KISDI OTT 1개 서비스 단일 월 구독료
}
WEEKS_PER_MONTH = 4.345
MONTHLY_TRIALS_PER_CATEGORY = 1 * WEEKS_PER_MONTH  # ≈ 4.345회/월/카테고리, 주 1회 순환


def simulate_month_v3(agent, pd_model=None, n_trials=None) -> float:
    """
    v3 미션 시행 구조 (공공 실측 통계 기반)
    - 카테고리당 주 1회 (월 4.345회) 미션 시행
    - 카테고리 예산 (합계 52,500원):
      * 배달: 22,000원 (aT 실측 청년 1인가구 배달팁 포함 결제액)
      * 쇼핑: 14,000원 (통계청 청년 의류·신발 지출 분해)
      * 카페: 9,000원 (KB국민카드 청년 커피전문점 건당 결제액)
      * 구독교통: 7,500원 (KISDI OTT 단일 계정 구독료)
    - 월 단위 이산화: 4회(65.5%) + 5회(34.5%)로 시행하여 월평균 4.345회 달성 (이론 기댓값 80,365원 보존)
    - 예산 상한 검증 (clamp 방어)
    - 배분:
      * 일반군: 70% 저축 / 30% 부채상환
      * 취약군(segment == '취약'): 50% 저축 / 50% 부채상환 (한국은행 기준 16.9% 고금리 리볼빙 우선 집중 상환)
    - 반환값: 당월 총 절감액 (total_saved)
    """
    if n_trials is None:
        base_trials = int(WEEKS_PER_MONTH)  # 4
        prob_extra = WEEKS_PER_MONTH - base_trials  # 0.345
        trials = base_trials + (1 if np.random.random() < prob_extra else 0)
    else:
        trials = int(n_trials)

    alpha = getattr(agent, "alpha", None)
    if alpha is None and isinstance(agent, dict):
        alpha = agent.get("alpha", 0.3523)
    elif alpha is None:
        alpha = 0.3523

    total_saved = 0.0
    for category, target in CATEGORY_ALLOCATION_V3.items():
        for _ in range(trials):
            if np.random.random() < alpha:
                total_saved += target

    # 예산 상한 검증 (자연 발생해야 함, 이론상 초과 불가하지만 방어적으로 clamp)
    max_possible = sum(CATEGORY_ALLOCATION_V3.values()) * trials
    total_saved = min(total_saved, max_possible)

    # 세그먼트별 상환 및 저축 배분
    segment = getattr(agent, "segment", None) or (agent.get("segment") if isinstance(agent, dict) else "일반")
    if segment == "취약":
        savings_ratio = 0.50
        debt_repay_ratio = 0.50
    else:
        savings_ratio = 0.70
        debt_repay_ratio = 0.30

    # 저축 적립
    if hasattr(agent, "savings"):
        agent.savings += total_saved * savings_ratio
    elif isinstance(agent, dict):
        agent["savings"] = agent.get("savings", 0.0) + total_saved * savings_ratio

    # 부채상환 반영
    limit = getattr(agent, "credit_limit", None)
    if limit is None and isinstance(agent, dict):
        limit = agent.get("credit_limit", 3000000.0)
    elif limit is None:
        limit = 3000000.0

    if hasattr(agent, "current_debt"):
        agent.current_debt = max(0.0, agent.current_debt - total_saved * debt_repay_ratio)
        agent.utilization = agent.current_debt / limit if limit > 0 else 0.0
    elif isinstance(agent, dict):
        agent["current_debt"] = max(0.0, agent.get("current_debt", 0.0) - total_saved * debt_repay_ratio)
        agent["utilization"] = agent["current_debt"] / limit if limit > 0 else 0.0

    return total_saved



# ==============================================================================
# 2절. 다층 목표자금 구조 (v3 신규)
# ==============================================================================
TARGET_FUND_TIERS = [
    {"amount": 3_000_000,  "months": 6,  "weight": 0.30, "label": "소액 챌린지"},
    {"amount": 7_000_000,  "months": 10, "weight": 0.40, "label": "중간 목표"},
    {"amount": 15_000_000, "months": 12, "weight": 0.30, "label": "목돈 목표"},
]


def get_tier_by_label(label: str) -> dict:
    for t in TARGET_FUND_TIERS:
        if t["label"] == label:
            return t
    raise KeyError(f"존재하지 않는 목표 티어 라벨: {label}")


def assign_target_tier(agent_index=None, n_agents=1000, tiers=TARGET_FUND_TIERS, seed=42):
    """
    2절 스펙: weight 비율대로 무작위 배정 (30% / 40% / 30%)
    """
    rng = np.random.default_rng(seed)
    labels = [t["label"] for t in tiers]
    weights = [t["weight"] for t in tiers]
    chosen = rng.choice(labels, size=n_agents, p=weights)
    if agent_index is not None and isinstance(agent_index, int):
        return chosen[agent_index]
    return chosen


def days_saved(agent, tier, monthly_extra_saving=None) -> float:
    """
    2절 스펙: 단축 일수 계산식
    required_monthly = tier["amount"] / tier["months"]
    new_monthly = required_monthly + monthly_extra_saving
    new_duration_months = tier["amount"] / new_monthly
    return (tier["months"] - new_duration_months) * 30
    """
    if isinstance(tier, str):
        tier = get_tier_by_label(tier)

    if monthly_extra_saving is None:
        if hasattr(agent, "savings"):
            months = getattr(agent, "target_months", tier["months"])
            monthly_extra_saving = agent.savings / float(months) if months > 0 else 0.0
        elif isinstance(agent, dict) and "savings" in agent:
            months = agent.get("target_months", tier["months"])
            monthly_extra_saving = agent["savings"] / float(months) if months > 0 else 0.0
        else:
            monthly_extra_saving = 0.0

    required_monthly = tier["amount"] / float(tier["months"])
    new_monthly = required_monthly + float(monthly_extra_saving)
    new_duration_months = tier["amount"] / new_monthly if new_monthly > 0 else 0.0
    return (tier["months"] - new_duration_months) * 30.0


if __name__ == "__main__":
    model, rep, auc = train_pd_model()
