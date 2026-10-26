import os
import pytest
import numpy as np
import pandas as pd
from simulation.generate_cohort_dataset import calculate_beta_params, generate_cohort, OUTPUT_PATH

def test_beta_params_moments():
    alpha_param, beta_param = calculate_beta_params()
    # mean should be ≈ 0.3523, common ≈ 29.29
    mean_val = alpha_param / (alpha_param + beta_param)
    assert 0.34 <= mean_val <= 0.36
    assert 9.0 <= alpha_param <= 11.5
    assert 17.5 <= beta_param <= 20.5

def test_generate_cohort_shape_and_columns():
    df = generate_cohort(n_agents=100, output_path=None)
    assert len(df) == 100
    expected_cols = [
        "user_id", "age", "occupation", "district", "income",
        "discretionary_budget", "total_card_usage", "credit_limit",
        "current_debt", "utilization", "alpha", "segment",
        "target_fund", "target_months"
    ]
    assert list(df.columns) == expected_cols

def test_alpha_distribution_range():
    df = generate_cohort(n_agents=1000, output_path=None)
    alphas = df["alpha"].values
    assert 0.30 <= np.mean(alphas) <= 0.40
    p5 = np.percentile(alphas, 5)
    p95 = np.percentile(alphas, 95)
    assert 0.15 <= p5 <= 0.30
    assert 0.40 <= p95 <= 0.55

def test_segment_classification():
    df = generate_cohort(n_agents=100, output_path=None)
    for _, row in df.iterrows():
        if row["utilization"] >= 0.5:
            assert row["segment"] == "취약"
        else:
            assert row["segment"] == "일반"
