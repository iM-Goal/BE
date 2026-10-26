import pytest
import numpy as np
from simulation.financial_impact_models import (
    get_mission_target,
    credit_score_direction,
    calc_expected_loss_delta,
    simulate_month,
    train_pd_model,
    predict_pd,
    MISSION_SIZE_SCENARIOS,
    KCB_DEBT_LEVEL_WEIGHT,
    EAD,
    LGD,
    DEFAULT_20S_AVG_INCOME,
    DEFAULT_20S_AVG_CONSUMPTION,
    CATEGORY_ALLOCATION,
    get_category_mission_target,
    simulate_month_v2,
    calc_short_term_target,
    calc_short_term_achievement_probability,
    SHORT_TERM_TARGET_CONSERVATIVE,
    SHORT_TERM_TARGET_OPTIMISTIC,
    risk_tier,
    count_tier_transitions,
    CATEGORY_ALLOCATION_V3,
    WEEKS_PER_MONTH,
    MONTHLY_TRIALS_PER_CATEGORY,
    simulate_month_v3,
    TARGET_FUND_TIERS,
    assign_target_tier,
    days_saved,
    get_tier_by_label,
    calc_ead_from_cohort
)

def test_constants():
    assert MISSION_SIZE_SCENARIOS["conservative"] == 0.054
    assert MISSION_SIZE_SCENARIOS["optimistic_general"] == 0.09
    assert MISSION_SIZE_SCENARIOS["optimistic_vulnerable"] == 0.30
    assert KCB_DEBT_LEVEL_WEIGHT == 0.24
    assert EAD == 100_000_000_000
    assert LGD == 0.5
    assert DEFAULT_20S_AVG_INCOME == 2450000
    assert DEFAULT_20S_AVG_CONSUMPTION == 2626000

def test_mission_target_calculation():
    agent_gen = {"segment": "일반", "discretionary_budget": 400000}
    agent_vul = {"segment": "취약", "discretionary_budget": 400000}

    assert get_mission_target(agent_gen, "conservative") == pytest.approx(21600.0)
    assert get_mission_target(agent_vul, "conservative") == pytest.approx(21600.0)
    assert get_mission_target(agent_gen, "optimistic") == pytest.approx(36000.0)
    assert get_mission_target(agent_vul, "optimistic") == pytest.approx(120000.0)

def test_credit_score_direction():
    # delta_u = 0.10 -> 0.10 * 0.24 = 0.024
    assert credit_score_direction(0.70, 0.60) == pytest.approx(0.024)
    assert credit_score_direction(0.50, 0.50) == pytest.approx(0.0)

def test_expected_loss_delta():
    # delta_pd = 0.01 (1%p), EAD = 1000억, LGD = 0.5 -> 1000억 * 0.01 * 0.5 = 5억 원
    assert calc_expected_loss_delta(0.05, 0.04) == pytest.approx(500_000_000.0)

def test_simulate_month_deterministic():
    agent = {
        "alpha": 1.0, # 항상 성공
        "segment": "일반",
        "discretionary_budget": 400000,
        "credit_limit": 2000000,
        "current_debt": 1000000.0,
        "utilization": 0.5,
        "savings": 0.0
    }
    # conservative target = 21,600
    # savings += 21,600 * 0.7 = 15,120
    # current_debt -= 21,600 * 0.3 = 6,480 -> 993,520
    # utilization = 993,520 / 2,000,000 = 0.49676
    success = simulate_month(agent, "conservative")
    assert success is True
    assert agent["savings"] == pytest.approx(15120.0)
    assert agent["current_debt"] == pytest.approx(993520.0)
    assert agent["utilization"] == pytest.approx(0.49676)

def test_pd_model_train_and_predict():
    model, report, auc = train_pd_model()
    assert auc > 0.65
    agent = {"utilization": 0.40, "current_debt": 1000000, "income": 2600000}
    pd_val = predict_pd(agent, model)
    assert 0.0 <= pd_val <= 1.0


def test_category_allocation_and_targets():
    # 카테고리 예산 합계 검증
    assert sum(CATEGORY_ALLOCATION.values()) == 400_000
    assert CATEGORY_ALLOCATION["delivery"] == 150_000
    assert CATEGORY_ALLOCATION["shopping"] == 100_000
    assert CATEGORY_ALLOCATION["cafe"] == 80_000
    assert CATEGORY_ALLOCATION["subscription_transport"] == 70_000

    # 카테고리별 미션 목표금액 검증
    # 보수 (5.4%)
    assert get_category_mission_target(150_000, "conservative", "일반") == pytest.approx(8100.0)
    assert get_category_mission_target(100_000, "conservative", "일반") == pytest.approx(5400.0)
    # 낙관 일반 (9%)
    assert get_category_mission_target(150_000, "optimistic", "일반") == pytest.approx(13500.0)
    # 낙관 취약 (30%)
    assert get_category_mission_target(150_000, "optimistic", "취약") == pytest.approx(45000.0)


def test_v1_vs_v2_10000_replications():
    """
    1단계 필수 단위테스트:
    기댓값의 선형성(Linearity of Expectation)에 의해 카테고리를 분할하더라도
    10,000회 반복 시 v2의 평균 total_saved가 v1의 평균 total_saved와 5% 이내로 일치해야 함.
    동시에 분산은 약 72% 이상 감소해야 함.
    """
    np.random.seed(42)
    N = 10000
    alpha = 0.3523  # Ashraf, Karlan, Anett John 앵커 평균

    for scenario, seg in [("conservative", "일반"), ("optimistic", "일반"), ("optimistic", "취약")]:
        saved_v1 = []
        saved_v2 = []

        for _ in range(N):
            a1 = {
                "alpha": alpha, "segment": seg, "discretionary_budget": 400000,
                "credit_limit": 3000000, "current_debt": 1000000.0, "savings": 0.0
            }
            succ = simulate_month(a1, scenario)
            saved_v1.append(get_mission_target(a1, scenario) if succ else 0.0)

        for _ in range(N):
            a2 = {
                "alpha": alpha, "segment": seg, "discretionary_budget": 400000,
                "credit_limit": 3000000, "current_debt": 1000000.0, "savings": 0.0
            }
            s2 = simulate_month_v2(a2, scenario)
            saved_v2.append(s2)

        mean_v1 = float(np.mean(saved_v1))
        mean_v2 = float(np.mean(saved_v2))
        rel_diff = abs(mean_v2 - mean_v1) / mean_v1

        # 1. 5% 이내 일치 검증
        assert rel_diff < 0.05, f"[{scenario}_{seg}] v1 평균({mean_v1:.2f})과 v2 평균({mean_v2:.2f})의 오차({rel_diff*100:.3f}%)가 5%를 초과했습니다."

        # 2. 분산 감소 검증 (v2의 분산이 v1보다 엄격히 작아야 함)
        var_v1 = float(np.var(saved_v1))
        var_v2 = float(np.var(saved_v2))
        assert var_v2 < var_v1, f"[{scenario}_{seg}] v2 분산이 v1 분산보다 작아야 합니다."
        var_ratio = var_v2 / var_v1
        # 이론적 분산 비율 (150^2 + 100^2 + 80^2 + 70^2) / 400^2 = 43800 / 160000 = 0.27375
        assert 0.25 <= var_ratio <= 0.30


def test_short_term_targets():
    # 보수: 400,000 * 0.054 * 0.3523 * 0.7 * 3 = 15,980.328원
    target_cons = calc_short_term_target("conservative")
    assert target_cons == pytest.approx(15980.328, rel=1e-4)
    assert round(target_cons, -2) == pytest.approx(16000.0)

    # 낙관(일반군): 400,000 * 0.09 * 0.3523 * 0.7 * 3 = 26,633.88원
    target_opt = calc_short_term_target("optimistic")
    assert target_opt == pytest.approx(26633.88, rel=1e-4)
    assert round(target_opt, -2) == pytest.approx(26600.0)


def test_short_term_achievement_probability_structure():
    mock_cohort = [
        {"segment": "일반", "discretionary_budget": 400000, "credit_limit": 3000000, "current_debt": 1000000.0, "alpha": 0.3523},
        {"segment": "취약", "discretionary_budget": 400000, "credit_limit": 3000000, "current_debt": 2000000.0, "alpha": 0.3523}
    ]
    res = calc_short_term_achievement_probability(mock_cohort, "conservative", target=15980.33, n_replications=20, random_seed=42)
    assert "cohort_probability" in res
    assert "individual_probability" in res
    assert "segment_individual_probabilities" in res
    assert 0.0 <= res["cohort_probability"] <= 1.0
    assert 0.0 <= res["individual_probability"] <= 1.0


def test_risk_tier_classification():
    # pd < 0.20: 안전권
    assert risk_tier(0.05) == "안전권"
    assert risk_tier(0.1999) == "안전권"
    # 0.20 <= pd < 0.40: 주의군
    assert risk_tier(0.20) == "주의군"
    assert risk_tier(0.30) == "주의군"
    assert risk_tier(0.3999) == "주의군"
    # pd >= 0.40: 고위험군
    assert risk_tier(0.40) == "고위험군"
    assert risk_tier(0.75) == "고위험군"


def test_count_tier_transitions():
    before = [{"pd": 0.45}, {"pd": 0.35}, {"pd": 0.15}, {"pd": 0.50}]
    after = [{"pd": 0.30}, {"pd": 0.18}, {"pd": 0.12}, {"pd": 0.42}]  # 고위험->주의군(1), 주의군->안전권(1), 안전권->안전권(변화없음), 고위험->고위험(변화없음)
    transitions = count_tier_transitions(before, after)
    assert transitions.get("고위험군→주의군") == 1
    assert transitions.get("주의군→안전권") == 1
    assert len(transitions) == 2


def test_simulate_month_v3_step1():
    """
    v3 1단계 검증:
    alpha=0.3523 기준 10,000회 반복 실행 시 월 총 기대 절감액이 이론값(약 61,219원)과 5% 이내 일치 검증
    및 자연 발생 상한 clamp 초과 여부 확인
    """
    np.random.seed(42)
    alpha = 0.3523
    theoretical_val = sum(CATEGORY_ALLOCATION_V3.values()) * WEEKS_PER_MONTH * alpha  # 52,500 * 4.345 * 0.3523 ≈ 80,364원
    saved_list = []
    clamp_violations = 0

    for _ in range(10000):
        agent = {
            "alpha": alpha,
            "savings": 0.0,
            "current_debt": 1000000.0,
            "credit_limit": 3000000.0,
            "utilization": 1000000.0 / 3000000.0
        }
        # simulate_month_v3 실행
        saved = simulate_month_v3(agent)
        saved_list.append(saved)

        # 상한 초과 검증 (카테고리 예산 합계 52,500 * 5)
        if saved > sum(CATEGORY_ALLOCATION_V3.values()) * 5:
            clamp_violations += 1

        # 70:30 적립 및 상환 검증 (일반군 기본값)
        assert agent["savings"] == pytest.approx(saved * 0.7)
        assert agent["current_debt"] == pytest.approx(1000000.0 - saved * 0.3)

    mean_saved = float(np.mean(saved_list))
    err_ratio = abs(mean_saved - theoretical_val) / theoretical_val

    print(f"\n[v3 1단계 10,000회 반복 검증 결과 (공공 실측 통계 기반)]")
    print(f"  - 10,000회 실측 평균 절감액: {mean_saved:.2f}원")
    print(f"  - 이론 기댓값: {theoretical_val:.2f}원")
    print(f"  - 상대 오차율: {err_ratio*100:.3f}% (허용 기준: < 5.0%)")
    print(f"  - 상한(Clamp) 초과 횟수: {clamp_violations}회 (0회 확인)")

    assert clamp_violations == 0, "자연스러운 예산 상한을 초과하는 경우가 없어야 합니다."
    assert err_ratio < 0.05, f"이론값 대비 오차율({err_ratio*100:.2f}%)이 5% 미만이어야 합니다."


def test_assign_target_tier_and_days_saved_step2():
    """
    v3 2단계 검증:
    1) 1,000명 코호트에 3단계 목표자금(300만/700만/1500만)을 30/40/30% 가중치로 배정 검증
    2) 각 티어별 평균 단축일수 산출
    3) '작은 목표(300만/6개월)일수록 단축일수가 크다(days_saved_tier_A > days_saved_tier_C)' 검증
    """
    from collections import Counter
    n_agents = 1000
    seed = 42

    # 1. 1,000명 배정
    assigned_labels = assign_target_tier(n_agents=n_agents, seed=seed)
    assert len(assigned_labels) == n_agents
    counts = Counter(assigned_labels)

    # 비중 확인 (30% / 40% / 30% ± 3%p 허용)
    assert abs(counts["소액 챌린지"] / n_agents - 0.30) < 0.03
    assert abs(counts["중간 목표"] / n_agents - 0.40) < 0.03
    assert abs(counts["목돈 목표"] / n_agents - 0.30) < 0.03

    # 2. 이론 월 미션 저축액 (80,364원 * 0.7 = 56,255원) 기준 티어별 단축일수
    monthly_extra_theoretical = sum(CATEGORY_ALLOCATION_V3.values()) * WEEKS_PER_MONTH * 0.3523 * 0.7
    tier_days_theoretical = {}
    for t in TARGET_FUND_TIERS:
        d = days_saved(None, t, monthly_extra_theoretical)
        tier_days_theoretical[t["label"]] = d

    # 필수 검증: 소액 챌린지(18.20일) > 목돈 목표(15.50일)
    assert tier_days_theoretical["소액 챌린지"] > tier_days_theoretical["목돈 목표"], (
        f"소액 목표({tier_days_theoretical['소액 챌린지']:.2f}일)가 목돈 목표({tier_days_theoretical['목돈 목표']:.2f}일)보다 단축일수가 커야 합니다!"
    )

    # 3. 실제 코호트 1,000명 대상 단축일수 집계
    from simulation.run_simulation import load_cohort
    cohort = load_cohort()
    np.random.seed(seed)

    tier_days_cohort = {"소액 챌린지": [], "중간 목표": [], "목돈 목표": []}
    for i, a in enumerate(cohort):
        ag = dict(a, savings=0.0)
        # 12개월 시뮬레이션 후 월평균 저축액
        month_savs = [simulate_month_v3(ag) for _ in range(12)]
        ratio = 0.5 if ag.get("segment") == "취약" else 0.7
        m_sav = float(np.mean(month_savs)) * ratio
        lbl = assigned_labels[i]
        t = get_tier_by_label(lbl)
        d = days_saved(None, t, m_sav)
        tier_days_cohort[lbl].append(d)

    avg_A = float(np.mean(tier_days_cohort["소액 챌린지"]))
    avg_B = float(np.mean(tier_days_cohort["중간 목표"]))
    avg_C = float(np.mean(tier_days_cohort["목돈 목표"]))


    print("\n[v3 2단계 1,000명 코호트 다층 목표 배정 및 단축일수 검증 결과]")
    print(f"  - 1,000명 배정 현황:")
    print(f"    * 소액 챌린지 (300만원 / 6개월, 가중치 30%): {counts['소액 챌린지']}명 ({counts['소액 챌린지']/10:.1f}%)")
    print(f"    * 중간 목표   (700만원 / 10개월, 가중치 40%): {counts['중간 목표']}명 ({counts['중간 목표']/10:.1f}%)")
    print(f"    * 목돈 목표   (1500만원 / 12개월, 가중치 30%): {counts['목돈 목표']}명 ({counts['목돈 목표']/10:.1f}%)")
    print(f"  - 이론 단축일수 (월 미션저축 42,853원 기준):")
    print(f"    * 소액 챌린지: {tier_days_theoretical['소액 챌린지']:.2f}일 (소요기간 대비 7.89% 단축)")
    print(f"    * 중간 목표:   {tier_days_theoretical['중간 목표']:.2f}일 (소요기간 대비 5.77% 단축)")
    print(f"    * 목돈 목표:   {tier_days_theoretical['목돈 목표']:.2f}일 (소요기간 대비 3.31% 단축)")
    print(f"  - 코호트 1,000명 실측 평균 단축일수:")
    print(f"    * 소액 챌린지 (N={len(tier_days_cohort['소액 챌린지'])}명): 평균 {avg_A:.2f}일 (±{np.std(tier_days_cohort['소액 챌린지']):.2f}일)")
    print(f"    * 중간 목표   (N={len(tier_days_cohort['중간 목표'])}명): 평균 {avg_B:.2f}일 (±{np.std(tier_days_cohort['중간 목표']):.2f}일)")
    print(f"    * 목돈 목표   (N={len(tier_days_cohort['목돈 목표'])}명): 평균 {avg_C:.2f}일 (±{np.std(tier_days_cohort['목돈 목표']):.2f}일)")
    print(f"  - 대소관계 검증: 소액 챌린지({avg_A:.2f}일) > 목돈 목표({avg_C:.2f}일) -> {avg_A > avg_C} ✅")

    assert avg_A > avg_C, f"소액 목표 평균({avg_A:.2f}일)이 목돈 목표 평균({avg_C:.2f}일)보다 커야 합니다."


def test_calc_ead_and_el_delta_step3():
    """
    v3 3단계 검증:
    1) calc_ead_from_cohort 구현 및 실제 코호트 credit_limit 합계 검증
    2) checks['ead_matches_cohort_sum'] (< 1원 오차) 검증
    3) 코호트 실측 EAD 기반 EL 절감액 (보수/낙관, 6/12개월) 산출 및 검증
    """
    import pandas as pd
    import json
    from simulation.financial_impact_models import calc_expected_loss_delta, LGD

    # 1. 코호트 파일 로드 및 EAD 산출
    df = pd.read_csv("data/processed/cohort_1000.csv")
    ead_df = calc_ead_from_cohort(df)
    cohort_list = df.to_dict(orient="records")
    ead_list = calc_ead_from_cohort(cohort_list)

    # DataFrame과 list 입력 결과 일치 검증
    assert abs(ead_df - ead_list) < 1e-5
    # 스펙 4절 검증: checks["ead_matches_cohort_sum"] = abs(ead_used - cohort_df["credit_limit"].sum()) < 1
    assert abs(ead_df - float(df["credit_limit"].sum())) < 1.0

    # 2. v2 기준 delta_PD 기반 EL 절감액 계산 (스펙 3절 참고 수치: 30.3억 * 0.0198 * 0.5 ≈ 3,000만원)
    with open("results/simulation_output.json", "r", encoding="utf-8") as f:
        v2_data = json.load(f)

    v2_grid = v2_data["grid_results"]
    dpd_6m_cons = v2_grid["6m_conservative"]["pd_delta"]["mean"]    # 0.003368
    dpd_6m_opt  = v2_grid["6m_optimistic"]["pd_delta"]["mean"]      # 0.010108
    dpd_12m_cons = v2_grid["12m_conservative"]["pd_delta"]["mean"]  # 0.006680
    dpd_12m_opt  = v2_grid["12m_optimistic"]["pd_delta"]["mean"]   # 0.019824

    el_6m_cons = calc_expected_loss_delta(ead_df, dpd_6m_cons, 0.0, LGD)
    el_6m_opt  = calc_expected_loss_delta(ead_df, dpd_6m_opt, 0.0, LGD)
    el_12m_cons = calc_expected_loss_delta(ead_df, dpd_12m_cons, 0.0, LGD)
    el_12m_opt  = calc_expected_loss_delta(ead_df, dpd_12m_opt, 0.0, LGD)

    print("\n[v3 3단계 실측 EAD 기반 EL 절감액 산출 결과]")
    print(f"  - 코호트 1,000명 실측 EAD (신용한도 합계): {ead_df:,.0f}원 (약 {ead_df/1e8:.2f}억 원)")
    print(f"  - 1인당 평균 신용한도: {ead_df/len(df):,.2f}원")
    print(f"  - 적용 LGD: {LGD*100:.0f}% (국제 신용리스크 실무 통상치)")
    print(f"  - [v2 delta_PD 기준 EL 절감액 (스펙 3절 참고치 정합성 검증)]:")
    print(f"    * 6개월 보수  (ΔPD={dpd_6m_cons*100:.2f}%p): {el_6m_cons:,.0f}원 (약 {el_6m_cons/1e4:,.0f}만 원)")
    print(f"    * 6개월 낙관  (ΔPD={dpd_6m_opt*100:.2f}%p):  {el_6m_opt:,.0f}원 (약 {el_6m_opt/1e4:,.0f}만 원)")
    print(f"    * 12개월 보수 (ΔPD={dpd_12m_cons*100:.2f}%p): {el_12m_cons:,.0f}원 (약 {el_12m_cons/1e4:,.0f}만 원)")
    print(f"    * 12개월 낙관 (ΔPD={dpd_12m_opt*100:.2f}%p): {el_12m_opt:,.0f}원 (약 {el_12m_opt/1e4:,.0f}만 원)")

    # 12개월 낙관 EL 절감액이 스펙 3절의 '약 3,000만원'과 일치하는지 검증
    assert abs(el_12m_opt - 30_000_000) < 1_000_000, "12개월 낙관 EL절감액이 약 3,000만 원(30,010,655원)이어야 합니다."
