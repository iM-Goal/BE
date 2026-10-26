import pytest
from simulation.mission_catalog import (
    GOVERNMENT_BENCHMARKS,
    MISSION_CATALOG,
    MISSION_PACKS,
    get_mission_by_id,
    get_package_summary
)

def test_government_benchmarks_valid():
    assert len(GOVERNMENT_BENCHMARKS) >= 7
    for k, bm in GOVERNMENT_BENCHMARKS.items():
        assert bm.unit_price > 0
        assert bm.source_org
        assert bm.survey_name
        assert bm.year >= 2023

def test_mission_catalog_matches_benchmarks():
    assert len(MISSION_CATALOG) >= 8
    for m in MISSION_CATALOG:
        assert m.unit_benchmark_key in GOVERNMENT_BENCHMARKS
        bm = GOVERNMENT_BENCHMARKS[m.unit_benchmark_key]
        assert m.unit_price == bm.unit_price
        assert m.monthly_target_amount == bm.unit_price * m.frequency_per_month
        assert m.weekly_equivalent == round(m.monthly_target_amount / 4.0)

def test_package_summaries():
    for pkey in ["light", "standard", "intensive"]:
        summary = get_package_summary(pkey)
        assert summary["monthly_total"] > 0
        assert summary["weekly_equivalent"] > 0
        assert len(summary["missions"]) >= 3
