"""
simulation/mission_catalog.py
정부 부처 및 공공기관 공시 실측 결제단가 기반 다양화된 iM GOAL 미션 카탈로그

[공식 통계 출처 닻(Anchor) 목록]
1. 배달음식: 농림축산식품부·한국농수산식품유통공사(aT) 『2023 외식경영실태조사』 1회 평균 결제 24,300원
2. 카페/음료: 한국소비자원 『참가격』 주요 15개 프랜차이즈 단일 음료 가중평균 4,200원 (객단가 6,200원)
3. 일반 외식: 농림축산식품부 『2023 외식소비행태조사』 20대 1인 방문 외식 16,500원
4. 모임 외식: 농림축산식품부 『2023 외식소비행태조사』 20대 주류 포함 저녁 회식/약속 1인 33,800원
5. 심야 택시: 카카오모빌리티 『2023 카카오모빌리티 리포트』 / 서울시 STIS 심야할증(22~03시) 평균 16,400원
6. 편의점: 산업통상자원부 『2023 주요 유통업체 매출동향』 편의점 1인 1회 객단가 7,200원
7. 디지털구독: 한국콘텐츠진흥원(문체부) 『2023 OTT 이용행태 조사 보고서』 1인당 1개 OTT 월평균 13,200원
"""

from dataclasses import dataclass
from typing import List, Dict

@dataclass(frozen=True)
class BenchmarkPrice:
    category: str
    item_name: str
    unit_price: int
    source_org: str
    survey_name: str
    year: int
    description: str


# 1. 정부·공공기관 공시 실측 단가 레지스트리 (Ground Truth)
GOVERNMENT_BENCHMARKS: Dict[str, BenchmarkPrice] = {
    "delivery_single": BenchmarkPrice(
        category="delivery",
        item_name="배달앱 1회 주문 결제액 (음식비+배달팁)",
        unit_price=24_300,
        source_org="농림축산식품부·한국농수산식품유통공사(aT)",
        survey_name="2023 외식경영실태조사",
        year=2023,
        description="배달앱 이용 가구 1회 주문당 실측 결제금액 (음식 21,300원 + 배달팁 3,000원)"
    ),
    "cafe_single_drink": BenchmarkPrice(
        category="cafe",
        item_name="커피전문점 1인 1잔 단일 음료",
        unit_price=4_200,
        source_org="한국소비자원 참가격",
        survey_name="커피 프랜차이즈 가격 동향",
        year=2023,
        description="주요 15개 프랜차이즈 아메리카노(3,700) 및 라떼(4,700) 가중평균가"
    ),
    "cafe_visit_basket": BenchmarkPrice(
        category="cafe",
        item_name="커피전문점 1인 1회 방문 객단가 (음료+디저트)",
        unit_price=6_200,
        source_org="농림축산식품부·aT",
        survey_name="2023 가공식품 세분시장 현황(커피류)",
        year=2023,
        description="커피전문점 방문 결제 1건당 평균 영수증 금액"
    ),
    "dining_normal": BenchmarkPrice(
        category="dining",
        item_name="20대 1인 일반 방문 외식 1회",
        unit_price=16_500,
        source_org="농림축산식품부",
        survey_name="2023 외식소비행태조사",
        year=2023,
        description="20대 청년 1인 식사 목적의 방문 외식 실측치"
    ),
    "dining_gathering": BenchmarkPrice(
        category="dining",
        item_name="주류 포함 저녁 회식·모임 외식 1인 지출",
        unit_price=33_800,
        source_org="농림축산식품부",
        survey_name="2023 외식소비행태조사",
        year=2023,
        description="20대 저녁 술자리/고깃집 모임 1인당 평균 결제 단가"
    ),
    "taxi_night": BenchmarkPrice(
        category="transport",
        item_name="20대 심야 할증(22~03시) 택시 1회 결제",
        unit_price=16_400,
        source_org="카카오모빌리티 / 서울시 STIS",
        survey_name="2023 카카오모빌리티 리포트",
        year=2023,
        description="심야 할증시간대 20대 평균 이동거리(8.5km) 기준 실측 결제요금"
    ),
    "convenience_store": BenchmarkPrice(
        category="convenience",
        item_name="편의점 1인 1회 구매단가 (객단가)",
        unit_price=7_200,
        source_org="산업통상자원부",
        survey_name="2023 주요 유통업체 매출동향",
        year=2023,
        description="주요 편의점 3사(CU, GS25, 세븐일레븐) 1건당 바스켓 결제액"
    ),
    "ott_subscription": BenchmarkPrice(
        category="subscription",
        item_name="1개 유료 OTT 서비스 월 결제단가",
        unit_price=13_200,
        source_org="한국콘텐츠진흥원(문체부 산하)",
        survey_name="2023 OTT 이용행태 조사 보고서",
        year=2023,
        description="넷플릭스(13,500), 티빙(9,500), 유튜브프리미엄(14,900) 실측 평균 결제단가"
    )
}


@dataclass
class MissionItem:
    mission_id: str
    title: str
    category: str
    level: str  # "Micro" (마이크로 습관), "Core" (핵심 지출), "Intensive" (집중 교정)
    unit_benchmark_key: str
    frequency_per_month: int
    monthly_target_amount: int
    weekly_equivalent: int
    action_guide: str

    @property
    def unit_price(self) -> int:
        return GOVERNMENT_BENCHMARKS[self.unit_benchmark_key].unit_price

    @property
    def citation(self) -> str:
        bm = GOVERNMENT_BENCHMARKS[self.unit_benchmark_key]
        return f"{bm.source_org} 『{bm.survey_name}』 ({bm.unit_price:,}원)"


# 2. 다양화된 실생활 미션 카탈로그 8종
MISSION_CATALOG: List[MissionItem] = [
    MissionItem(
        mission_id="MSN_CAFE_01",
        title="텀블러 데이 (카페 음료 1잔 대체)",
        category="cafe",
        level="Micro",
        unit_benchmark_key="cafe_single_drink",
        frequency_per_month=4,
        monthly_target_amount=16_800,  # 4,200 * 4
        weekly_equivalent=4_200,
        action_guide="주 1회 프랜차이즈 커피 대신 텀블러/사내 커피 이용하기"
    ),
    MissionItem(
        mission_id="MSN_CONV_01",
        title="편의점 야식 세이프 챌린지",
        category="convenience",
        level="Micro",
        unit_benchmark_key="convenience_store",
        frequency_per_month=2,
        monthly_target_amount=14_400,  # 7,200 * 2
        weekly_equivalent=3_600,
        action_guide="늦은 밤 무의식적인 편의점 간식/주류 충동결제 1회 참기"
    ),
    MissionItem(
        mission_id="MSN_TAXI_01",
        title="막차 세이프 (심야 택시 1회 방어)",
        category="transport",
        level="Micro",
        unit_benchmark_key="taxi_night",
        frequency_per_month=1,
        monthly_target_amount=16_400,  # 16,400 * 1
        weekly_equivalent=4_100,
        action_guide="자정 전 지하철/버스 막차 타고 귀가하여 심야 할증 택시비 아끼기"
    ),
    MissionItem(
        mission_id="MSN_DELI_01",
        title="주말 배달 파업 (배달 1회 컷)",
        category="delivery",
        level="Core",
        unit_benchmark_key="delivery_single",
        frequency_per_month=2,
        monthly_target_amount=48_600,  # 24,300 * 2
        weekly_equivalent=12_150,
        action_guide="주말 배달앱 주문을 냉장고 파먹기(집밥) 또는 간편식으로 대체"
    ),
    MissionItem(
        mission_id="MSN_DINE_01",
        title="약속 1차로 끝내기 (모임 외식 1회 컷)",
        category="dining",
        level="Core",
        unit_benchmark_key="dining_gathering",
        frequency_per_month=1,
        monthly_target_amount=33_800,  # 33,800 * 1
        weekly_equivalent=8_450,
        action_guide="고비용 2차 술자리/외식 모임 대신 1차 후 양해 구하고 귀가하기"
    ),
    MissionItem(
        mission_id="MSN_OTT_01",
        title="잠자는 구독 다이어트 (OTT 1개 해지)",
        category="subscription",
        level="Core",
        unit_benchmark_key="ott_subscription",
        frequency_per_month=1,
        monthly_target_amount=13_200,  # 13,200 * 1
        weekly_equivalent=3_300,
        action_guide="최근 2주간 시청 이력 없는 유료 OTT 서비스 1개 해지 또는 정지"
    ),
    MissionItem(
        mission_id="MSN_DELI_INTENSIVE",
        title="배달 디톡스 (배달 3회 컷)",
        category="delivery",
        level="Intensive",
        unit_benchmark_key="delivery_single",
        frequency_per_month=3,
        monthly_target_amount=72_900,  # 24,300 * 3
        weekly_equivalent=18_225,
        action_guide="과소비 취약군을 위한 강력한 배달 지출 차단 챌린지"
    ),
    MissionItem(
        mission_id="MSN_TAXI_INTENSIVE",
        title="칼퇴 & 대중교통 챌린지 (심야택시 2회 컷)",
        category="transport",
        level="Intensive",
        unit_benchmark_key="taxi_night",
        frequency_per_month=2,
        monthly_target_amount=32_800,  # 16,400 * 2
        weekly_equivalent=8_200,
        action_guide="월 2회 심야 택시 탑승을 방지하여 고액 교통비 절감"
    ),
]


# 3. 사용자 페르소나 및 시뮬레이션 매핑용 3대 패키지 (Mission Packs)
MISSION_PACKS = {
    "light": {
        "name": "Light 팩 (보수형 / 가벼운 첫걸음)",
        "mission_ids": ["MSN_CAFE_01", "MSN_CONV_01", "MSN_TAXI_01"],
        "monthly_total": 47_600,   # 16.8k + 14.4k + 16.4k
        "weekly_equivalent": 11_900,
        "discretionary_ratio": 0.119,  # 47,600 / 400,000 (11.9%)
        "target_persona": "절약 습관 형성이 필요한 사회초년생 / 보수적 넛지 대상"
    },
    "standard": {
        "name": "Standard 팩 (사용자 제안: 주 2.5만 원 실천형)",
        "mission_ids": ["MSN_DELI_01", "MSN_DINE_01", "MSN_CAFE_01", "MSN_OTT_01"],
        "monthly_total": 112_400,  # 48.6k + 33.8k + 16.8k + 13.2k
        "weekly_equivalent": 28_100,  # 주당 약 2.8만 원 (약 2.5만 원!)
        "discretionary_ratio": 0.281,  # 112,400 / 400,000 (28.1%)
        "target_persona": "목표자금 1,000만 원 조기 달성을 희망하는 일반 청년"
    },
    "intensive": {
        "name": "Intensive 팩 (취약군 과소비 집중 교정형)",
        "mission_ids": ["MSN_DELI_INTENSIVE", "MSN_DINE_01", "MSN_TAXI_INTENSIVE", "MSN_CAFE_01"],
        "monthly_total": 156_300,  # 72.9k + 33.8k + 32.8k + 16.8k
        "weekly_equivalent": 39_075,
        "discretionary_ratio": 0.391,  # 156,300 / 400,000 (39.1%)
        "target_persona": "카드 리볼빙/연체 위험이 있는 고위험 다중채무 취약 차주"
    }
}


def get_mission_by_id(mission_id: str) -> MissionItem:
    for m in MISSION_CATALOG:
        if m.mission_id == mission_id:
            return m
    raise KeyError(f"존재하지 않는 미션 ID: {mission_id}")


def get_missions_for_category(category: str) -> List[MissionItem]:
    return [m for m in MISSION_CATALOG if m.category == category]


def get_package_summary(pack_key: str) -> Dict:
    pack = MISSION_PACKS.get(pack_key)
    if not pack:
        raise KeyError(f"존재하지 않는 팩: {pack_key}")

    missions = [get_mission_by_id(mid) for mid in pack["mission_ids"]]
    return {
        "package_name": pack["name"],
        "monthly_total": pack["monthly_total"],
        "weekly_equivalent": pack["weekly_equivalent"],
        "discretionary_ratio": pack["discretionary_ratio"],
        "target_persona": pack["target_persona"],
        "missions": [
            {
                "id": m.mission_id,
                "title": m.title,
                "monthly_amount": m.monthly_target_amount,
                "weekly_amount": m.weekly_equivalent,
                "source": m.citation
            }
            for m in missions
        ]
    }
