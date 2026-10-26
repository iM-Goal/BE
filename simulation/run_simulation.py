"""
simulation/run_simulation.py
iM GOAL 효과 검증을 위한 몬테카를로 ABM 그리드 시뮬레이션 엔진 v2
(카테고리 분할 + 이중목표 + 리스크등급 전환 지표 탑재)
스펙: iM GOAL 효과 검증 시뮬레이션 상세 구현 스펙 v2
"""

import os
import sys
import json
import time
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from simulation.financial_impact_models import (
    train_pd_model,
    simulate_month,
    simulate_month_v2,
    get_mission_target,
    get_category_mission_target,
    credit_score_direction,
    calc_expected_loss_delta,
    calc_short_term_target,
    calc_short_term_achievement_probability,
    risk_tier,
    count_tier_transitions,
    CATEGORY_ALLOCATION,
    SHORT_TERM_TARGET_CONSERVATIVE,
    SHORT_TERM_TARGET_OPTIMISTIC,
    LONG_TERM_TARGET,
    LONG_TERM_MONTHS,
    MISSION_SIZE_SCENARIOS,
    KCB_DEBT_LEVEL_WEIGHT,
    EAD,
    LGD,
    DEFAULT_20S_AVG_INCOME
)

COHORT_PATH = "data/processed/cohort_1000.csv"
OUTPUT_JSON_PATH = "results/simulation_output.json"


def load_cohort(path: str = COHORT_PATH) -> list[dict]:
    if not os.path.exists(path):
        raise FileNotFoundError(f"코호트 파일이 존재하지 않습니다: {path}")
    df = pd.read_csv(path)
    return df.to_dict(orient="records")


def batch_predict_pd(agents: list[dict], pd_model) -> np.ndarray:
    """
    에이전트 전체의 PD를 벡터 연산으로 고속 배치 예측
    """
    utilizations = np.array([float(a["utilization"]) for a in agents])
    debts = np.array([float(a["current_debt"]) for a in agents])
    incomes = np.array([float(a.get("income", DEFAULT_20S_AVG_INCOME)) for a in agents])
    debt_ratios = np.where(incomes > 0, debts / incomes, 0.0)

    X = pd.DataFrame({"utilization": utilizations, "debt_ratio": debt_ratios})
    return pd_model.predict_proba(X)[:, 1]


def summarize_replication(agents: list[dict], baseline_snapshot: dict, months: int, pd_model) -> dict:
    """
    단일 replication 종료 후 코호트 1,000명에 대한 4대 핵심 지표 집계
    1) 목표자금 달성기한 단축 (days_saved)
    2) 목표자금 달성률 (achievement_rate)
    3) 연체율(PD) 변화 (pd_delta)
    4) B2B 예상손실 절감액 (el_delta)
    + 부채수준 기여도 변화 (credit_contrib_change)
    + 재량지출 절감률 (reduction_rate)
    """
    pds_initial = baseline_snapshot["pds"]
    utils_initial = baseline_snapshot["utilizations"]
    target_funds = baseline_snapshot["target_funds"]
    target_months = baseline_snapshot["target_months"]
    discretionary_budgets = baseline_snapshot["discretionary_budgets"]

    pds_final = batch_predict_pd(agents, pd_model)
    utils_final = np.array([float(a["utilization"]) for a in agents])
    savings = np.array([float(a["savings"]) for a in agents])

    # 1. 목표자금 달성률 (누적 미션 저축액 / 목표자금 10,000,000원)
    achievement_rates = savings / target_funds
    achievement_rate = float(np.mean(achievement_rates))

    # 2. 목표 달성 단축 일수:
    # 계획 저축속도 = target_fund / target_months (1,000,000원/월)
    # 미션 절감 저축속도 = savings / months
    # 새 달성 소요월 = target_fund / (1,000,000 + savings/months)
    # 단축 일수 = (target_months - 새 달성 소요월) * 30일
    monthly_bonus = savings / float(months)
    planned_monthly_saving = target_funds / target_months
    new_months_needed = target_funds / (planned_monthly_saving + monthly_bonus)
    months_saved = target_months - new_months_needed
    days_saved = float(np.mean(months_saved * 30.0))

    # 3. PD 변화
    pd_delta = float(np.mean(pds_initial - pds_final))
    pd_baseline = float(np.mean(pds_initial))
    pd_final_mean = float(np.mean(pds_final))

    # 4. B2B 예상손실 절감액 (Basel 표준식: EAD * delta_PD * LGD)
    el_delta = calc_expected_loss_delta(pd_baseline, pd_final_mean, ead=EAD, lgd=LGD)

    # 5. KCB 신용점수 부채수준 항목 상대 기여도 변화 (delta_u * 0.24)
    credit_contrib = credit_score_direction(float(np.mean(utils_initial)), float(np.mean(utils_final)))

    # 6. 재량지출 절감 비율 (월평균 절감총액 / 재량지출 40만)
    # 총 절감액 = savings / 0.7 (70% 저축, 30% 상환)
    total_savings_generated = savings / 0.7
    monthly_saved_per_agent = total_savings_generated / float(months)
    reduction_rate = float(np.mean(monthly_saved_per_agent / discretionary_budgets))

    return {
        "achievement_rate": achievement_rate,
        "days_saved": days_saved,
        "pd_baseline": pd_baseline,
        "pd_final": pd_final_mean,
        "pd_delta": pd_delta,
        "el_delta": el_delta,
        "credit_contrib_change": credit_contrib,
        "reduction_rate": reduction_rate,
        "mean_savings": float(np.mean(savings))
    }


def aggregate_with_ci(replication_results: list[dict]) -> dict:
    """
    200회 반복 결과에 대한 평균, 표준편차, 95% 신뢰구간 산출
    """
    agg = {}
    keys = replication_results[0].keys()
    n = len(replication_results)

    for k in keys:
        vals = np.array([r[k] for r in replication_results])
        mean_val = float(np.mean(vals))
        std_val = float(np.std(vals, ddof=1)) if n > 1 else 0.0
        ci_lower = float(np.percentile(vals, 2.5))
        ci_upper = float(np.percentile(vals, 97.5))
        agg[k] = {
            "mean": mean_val,
            "std": std_val,
            "ci_95_lower": ci_lower,
            "ci_95_upper": ci_upper
        }
    return agg


def run_grid_v2(cohort: list[dict], pd_model, baseline_snapshot: dict, months_list=(6, 12), scenarios=("conservative", "optimistic"), n_replications=200):
    """
    6절. 그리드 시뮬레이션 v2 (카테고리 분할 simulate_month_v2 기반, 200회 반복)
    """
    grid_results = {}
    segment_breakdown_12m_opt = {}
    gen_idx = [i for i, a in enumerate(cohort) if a["segment"] == "일반"]
    vul_idx = [i for i, a in enumerate(cohort) if a["segment"] == "취약"]

    for months in months_list:
        for scenario in scenarios:
            cell_key = f"{months}m_{scenario}"
            replication_results = []
            gen_deltas, vul_deltas, gen_savings, vul_savings = [], [], [], []

            for rep in range(n_replications):
                agents = [dict(a, savings=0.0) for a in cohort]
                for m in range(months):
                    for a in agents:
                        simulate_month_v2(a, scenario, pd_model)

                rep_summary = summarize_replication(agents, baseline_snapshot, months, pd_model)
                replication_results.append(rep_summary)

                if months == 12 and scenario == "optimistic":
                    final_pds = batch_predict_pd(agents, pd_model)
                    p_diff = baseline_snapshot["pds"] - final_pds
                    sav = np.array([a["savings"] for a in agents])
                    gen_deltas.append(float(np.mean(p_diff[gen_idx])))
                    vul_deltas.append(float(np.mean(p_diff[vul_idx])))
                    gen_savings.append(float(np.mean(sav[gen_idx])))
                    vul_savings.append(float(np.mean(sav[vul_idx])))

            if months == 12 and scenario == "optimistic":
                segment_breakdown_12m_opt = {
                    "general": {
                        "count": len(gen_idx),
                        "mean_savings": float(np.mean(gen_savings)),
                        "pd_delta_mean": float(np.mean(gen_deltas))
                    },
                    "vulnerable": {
                        "count": len(vul_idx),
                        "mean_savings": float(np.mean(vul_savings)),
                        "pd_delta_mean": float(np.mean(vul_deltas))
                    },
                    "pd_improvement_ratio": float(np.mean(vul_deltas) / np.mean(gen_deltas)) if np.mean(gen_deltas) > 0 else 1.0
                }

            grid_results[cell_key] = aggregate_with_ci(replication_results)
            print(f"  [Grid 완료] {cell_key:15s} -> EL 절감: {grid_results[cell_key]['el_delta']['mean']:,.0f}원, PD 감소: {grid_results[cell_key]['pd_delta']['mean']:.4f}, 단축일수: {grid_results[cell_key]['days_saved']['mean']:.2f}일")

    return grid_results, segment_breakdown_12m_opt


def run_sensitivity_sweep_v2(cohort: list[dict], pd_model, baseline_snapshot: dict, scenario="conservative", months=12, n_replications=200):
    """
    6절 & 7절. 민감도 분석 그리드 v2: alpha 0% ~ 100% 11단계 스윕
    """
    alpha_levels = [round(x, 2) for x in np.linspace(0.0, 1.0, 11)]
    sensitivity_results = []

    print(f"  [Sensitivity Sweep] {scenario} {months}m (alpha 0.0 ~ 1.0, 11단계)...")
    for alpha_val in alpha_levels:
        rep_list = []
        for rep in range(n_replications):
            agents = [dict(a, alpha=alpha_val, savings=0.0) for a in cohort]
            for m in range(months):
                for a in agents:
                    simulate_month_v2(a, scenario, pd_model)
            rep_list.append(summarize_replication(agents, baseline_snapshot, months, pd_model))

        agg = aggregate_with_ci(rep_list)
        step_summary = {
            "alpha": alpha_val,
            "achievement_rate": agg["achievement_rate"]["mean"],
            "days_saved": agg["days_saved"]["mean"],
            "pd_delta": agg["pd_delta"]["mean"],
            "el_delta": agg["el_delta"]["mean"],
            "credit_contrib_change": agg["credit_contrib_change"]["mean"],
            "reduction_rate": agg["reduction_rate"]["mean"]
        }
        sensitivity_results.append(step_summary)

    return sensitivity_results


def run_tier_transitions_grid(cohort: list[dict], pd_model, baseline_snapshot: dict, n_replications=200):
    """
    3절. 리스크등급 전환 전/후 집계 및 200회 신뢰구간 산출
    """
    tier_results = {}
    cohort_with_base_pd = [dict(a, pd=float(baseline_snapshot["pds"][i])) for i, a in enumerate(cohort)]

    for scenario in ["conservative", "optimistic"]:
        for months in [6, 12]:
            cell_key = f"{months}m_{scenario}"

            # 단일 Seed 42 실행
            np.random.seed(42)
            agents_seed42 = [dict(a, savings=0.0) for a in cohort_with_base_pd]
            for m in range(months):
                for a in agents_seed42:
                    simulate_month_v2(a, scenario, pd_model)

            pds_seed = batch_predict_pd(agents_seed42, pd_model)
            for i, a in enumerate(agents_seed42):
                a["pd"] = float(pds_seed[i])

            trans_seed = count_tier_transitions(cohort_with_base_pd, agents_seed42)
            after_counts_seed = pd.Series([risk_tier(a["pd"]) for a in agents_seed42]).value_counts().to_dict()

            # 200회 반복 집계
            all_trans = {}
            high_mig_list = []
            mid_mig_list = []

            for rep in range(n_replications):
                agents = [dict(a, savings=0.0) for a in cohort_with_base_pd]
                for m in range(months):
                    for a in agents:
                        simulate_month_v2(a, scenario, pd_model)

                pds_rep = batch_predict_pd(agents, pd_model)
                for i, a in enumerate(agents):
                    a["pd"] = float(pds_rep[i])

                tr = count_tier_transitions(cohort_with_base_pd, agents)
                for k, v in tr.items():
                    all_trans[k] = all_trans.get(k, []) + [v]

                h_cnt = tr.get("고위험군→주의군", 0) + tr.get("고위험군→안전권", 0)
                m_cnt = tr.get("주의군→안전권", 0)
                high_mig_list.append(h_cnt)
                mid_mig_list.append(m_cnt)

            trans_agg = {}
            for k in all_trans.keys():
                vals = all_trans[k] + [0] * (n_replications - len(all_trans[k]))
                trans_agg[k] = {
                    "mean": float(np.mean(vals)),
                    "ci_95_lower": float(np.percentile(vals, 2.5)),
                    "ci_95_upper": float(np.percentile(vals, 97.5))
                }

            tier_results[cell_key] = {
                "seed42_transitions": trans_seed,
                "seed42_after_counts": after_counts_seed,
                "replications_transitions": trans_agg,
                "high_risk_migration_mean": float(np.mean(high_mig_list)),
                "high_risk_migration_rate": float(np.mean(high_mig_list) / 217.0),
                "mid_risk_migration_mean": float(np.mean(mid_mig_list))
            }

    return tier_results


def run_v1_vs_v2_comparison(cohort: list[dict], n_iterations=10000, alpha=0.3523):
    """
    6번 검증용: v1 vs v2 10,000회 기댓값 일치 검증
    """
    saved_v1 = []
    saved_v2 = []
    np.random.seed(42)

    for _ in range(n_iterations):
        a1 = {"alpha": alpha, "segment": "일반", "discretionary_budget": 400000, "credit_limit": 3000000, "current_debt": 1000000.0, "savings": 0.0}
        succ = simulate_month(a1, "conservative")
        saved_v1.append(get_mission_target(a1, "conservative") if succ else 0.0)

    for _ in range(n_iterations):
        a2 = {"alpha": alpha, "segment": "일반", "discretionary_budget": 400000, "credit_limit": 3000000, "current_debt": 1000000.0, "savings": 0.0}
        s2 = simulate_month_v2(a2, "conservative")
        saved_v2.append(s2)

    m1 = float(np.mean(saved_v1))
    m2 = float(np.mean(saved_v2))
    return {
        "v1_mean_saved": m1,
        "v2_mean_saved": m2,
        "relative_diff": abs(m2 - m1) / m1,
        "variance_ratio": float(np.var(saved_v2) / np.var(saved_v1))
    }


def main():
    print("==================================================================")
    print("  iM GOAL 효과 검증 ABM 시뮬레이션 엔진 v2 구동 시작")
    print("  (카테고리 분할 + 이중목표 + 리스크등급 전환)")
    print("==================================================================")
    t_start = time.time()

    # 1. 코호트 로드 및 PD 모델 학습
    print("[1/5] 코호트 로드 및 PD 로지스틱 회귀 모델 학습...")
    cohort = load_cohort(COHORT_PATH)
    print(f"  - 코호트 규모: {len(cohort)}명 로드 완료")
    pd_model, _, auc = train_pd_model()
    print(f"  - PD 모델 학습 완료 (ROC-AUC: {auc:.4f})")

    baseline_pds = batch_predict_pd(cohort, pd_model)
    baseline_snapshot = {
        "pds": baseline_pds,
        "utilizations": np.array([float(a["utilization"]) for a in cohort]),
        "target_funds": np.array([float(a["target_fund"]) for a in cohort]),
        "target_months": np.array([float(a["target_months"]) for a in cohort]),
        "discretionary_budgets": np.array([float(a["discretionary_budget"]) for a in cohort]),
        "incomes": np.array([float(a.get("income", DEFAULT_20S_AVG_INCOME)) for a in cohort])
    }
    base_tiers = pd.Series([risk_tier(p) for p in baseline_pds]).value_counts().to_dict()

    # 2. 그리드 시뮬레이션 (6m/12m x conservative/optimistic, 200회)
    print("\n[2/5] 4개 주요 조합 그리드 시뮬레이션 (v2, 200회 반복)...")
    grid_results, segment_breakdown_opt_12m = run_grid_v2(
        cohort=cohort,
        pd_model=pd_model,
        baseline_snapshot=baseline_snapshot,
        months_list=(6, 12),
        scenarios=("conservative", "optimistic"),
        n_replications=200
    )

    # 3. 민감도 분석 (alpha 0% ~ 100% 11단계 스윕)
    print("\n[3/5] 민감도 분석 (alpha 0.0~1.0 11단계 스윕, 각 200회 반복)...")
    sens_cons = run_sensitivity_sweep_v2(cohort, pd_model, baseline_snapshot, "conservative", 12, 200)
    sens_opt = run_sensitivity_sweep_v2(cohort, pd_model, baseline_snapshot, "optimistic", 12, 200)

    # 4. 단기목표(3개월 챌린지) 달성 확률 산출
    print("\n[4/5] 단기목표 3개월 챌린지 달성 확률 산출 (200회 반복)...")
    short_term_cons = calc_short_term_achievement_probability(cohort, "conservative", target=SHORT_TERM_TARGET_CONSERVATIVE, n_replications=200, pd_model=pd_model, random_seed=42)
    short_term_opt = calc_short_term_achievement_probability(cohort, "optimistic", target=SHORT_TERM_TARGET_OPTIMISTIC, n_replications=200, pd_model=pd_model, random_seed=42)

    # 5. 리스크 등급 전환 지표 산출
    print("\n[5/5] 리스크 등급 전환 인원 산출 (200회 반복)...")
    tier_transitions = run_tier_transitions_grid(cohort, pd_model, baseline_snapshot, n_replications=200)

    # v1 vs v2 기댓값 보존 검증 (10,000회)
    print("\n[*] v1 vs v2 기댓값 보존 10,000회 검증...")
    v1_v2_comp = run_v1_vs_v2_comparison(cohort, n_iterations=10000, alpha=0.3523)
    print(f"  - v1 평균: {v1_v2_comp['v1_mean_saved']:.2f}원, v2 평균: {v1_v2_comp['v2_mean_saved']:.2f}원 (오차: {v1_v2_comp['relative_diff']*100:.3f}%)")

    # 결과 패키징
    output_data = {
        "metadata": {
            "version": "v2_category_split_dual_target",
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "cohort_size": len(cohort),
            "n_replications": 200,
            "target_fund_anchor": LONG_TERM_TARGET,
            "target_months_anchor": LONG_TERM_MONTHS,
            "ead_anchor": EAD,
            "lgd_anchor": LGD,
            "kcb_debt_weight": KCB_DEBT_LEVEL_WEIGHT,
            "mission_size_scenarios": MISSION_SIZE_SCENARIOS,
            "category_allocation": CATEGORY_ALLOCATION,
            "execution_time_seconds": round(time.time() - t_start, 2)
        },
        "baseline_summary": {
            "mean_utilization": float(np.mean(baseline_snapshot["utilizations"])),
            "mean_pd": float(np.mean(baseline_snapshot["pds"])),
            "mean_discretionary_budget": float(np.mean(baseline_snapshot["discretionary_budgets"])),
            "mean_income": float(np.mean(baseline_snapshot["incomes"])),
            "risk_tier_counts": base_tiers
        },
        "grid_results": grid_results,
        "segment_breakdown_optimistic_12m": segment_breakdown_opt_12m,
        "sensitivity_conservative": sens_cons,
        "sensitivity_optimistic": sens_opt,
        "short_term_challenges": {
            "conservative": short_term_cons,
            "optimistic": short_term_opt
        },
        "tier_transitions": tier_transitions,
        "v1_v2_comparison": v1_v2_comp
    }

    os.makedirs(os.path.dirname(OUTPUT_JSON_PATH), exist_ok=True)
    with open(OUTPUT_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(output_data, f, indent=2, ensure_ascii=False)

    print(f"\n[OK] 시뮬레이션 v2 완료! 결과가 {OUTPUT_JSON_PATH} 에 저장되었습니다. (총 소요시간: {time.time()-t_start:.2f}초)")


if __name__ == "__main__":
    main()
