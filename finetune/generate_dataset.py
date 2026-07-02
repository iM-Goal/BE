"""
iM AgentiX 파인튜닝 데이터셋 생성 스크립트
- 목표 파싱 데이터셋: 500건 (자연어 → JSON)
- 미션 생성 데이터셋: 300건 (소비패턴 → 미션 JSON)
출력: finetune/data/goal_parsing.jsonl, finetune/data/mission_generation.jsonl
"""
import json
import random
import os

# 스크립트 파일 위치를 기준으로 출력 디렉토리 경로 지정
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
os.makedirs(DATA_DIR, exist_ok=True)

# ── 목표 파싱 데이터셋 ──
ITEMS = [
    ("나이키 에어맥스", 128000, "패션"),
    ("아이패드 프로", 1299000, "전자기기"),
    ("다이슨 에어랩", 598000, "뷰티"),
    ("맥북 에어 M2", 1590000, "전자기기"),
    ("갤럭시 버즈", 179000, "전자기기"),
    ("코치 가방", 450000, "패션"),
    ("소니 헤드폰", 380000, "전자기기"),
    ("구찌 지갑", 680000, "패션"),
    ("필립스 전동칫솔", 89000, "생활용품"),
    ("닌텐도 스위치", 360000, "게임"),
    ("에어팟 프로", 359000, "전자기기"),
    ("발뮤다 토스터", 198000, "주방"),
    ("삼성 갤럭시 탭", 899000, "전자기기"),
    ("키친에이드 믹서", 750000, "주방"),
    ("캠핑 텐트", 320000, "스포츠"),
    ("요가매트", 58000, "스포츠"),
    ("블루투스 스피커", 120000, "전자기기"),
    ("향수", 180000, "뷰티"),
    ("전동 킥보드", 480000, "교통"),
    ("커피머신", 250000, "주방"),
]

NATURAL_LANG_TEMPLATES = [
    "{item} 사고싶어",
    "{item} 갖고싶다",
    "{item} 살 돈 모으고싶어",
    "{item} 구매하고 싶은데 돈을 모아야해",
    "요즘 {item} 탐나는데 저축해볼까",
    "{item} 너무 갖고싶어서 모으기 시작했어",
    "{item} 사려고 돈 모으는 중",
    "친구가 {item} 산거 보고 나도 갖고싶어졌어",
    "{item} 생일선물로 받고싶은데 스스로 살까",
    "올해 안에 {item} 꼭 사고싶어",
    "{item} 세일할 때 사려고 미리 모으는 중",
    "드디어 {item} 사기로 결심했어",
    "{item} 없으면 안될것 같아서",
    "{item} 신제품 나왔는데 너무 탐나",
    "작년부터 {item} 갖고싶었는데 이제 모아볼게",
]

URL_TEMPLATES = [
    "https://www.coupang.com/vp/products/{item}",
    "https://www.musinsa.com/products/{item}",
    "https://www.apple.com/kr/shop/{item}",
    "https://shopping.naver.com/search/{item}",
]

def generate_goal_parsing_dataset(n=500):
    data = []
    for _ in range(n):
        item_name, price, category = random.choice(ITEMS)
        price_variation = price * random.uniform(0.9, 1.1)
        price_final = int(price_variation // 1000) * 1000

        reg_type = random.choice(["NATURAL_LANGUAGE", "URL", "NATURAL_LANGUAGE", "NATURAL_LANGUAGE"])

        if reg_type == "NATURAL_LANGUAGE":
            template = random.choice(NATURAL_LANG_TEMPLATES)
            raw_input = template.format(item=item_name)
        else:
            url_template = random.choice(URL_TEMPLATES)
            raw_input = url_template.format(item=item_name.replace(" ", "-").lower())

        output = {
            "itemName": item_name,
            "price": price_final,
            "category": category
        }

        # MLX LoRA 학습 형식
        data.append({
            "prompt": f"""당신은 금융 목표 파싱 AI입니다.
사용자의 입력에서 물품명과 가격을 추출해 반드시 아래 JSON 형식으로만 응답하세요.
{{"itemName": "물품명", "price": 숫자, "category": "카테고리"}}

입력: {raw_input}""",
            "completion": json.dumps(output, ensure_ascii=False)
        })

    output_file = os.path.join(DATA_DIR, "goal_parsing.jsonl")
    with open(output_file, "w", encoding="utf-8") as f:
        for item in data:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"목표 파싱 데이터셋 생성 완료: {len(data)}건 → {output_file}")
    return data

# ── 미션 생성 데이터셋 ──
CATEGORIES = ["카페", "식비", "교통", "편의점", "배달음식", "쇼핑", "엔터테인먼트", "의류"]
GOAL_ITEMS = ["나이키 에어맥스", "아이패드", "다이슨 에어랩", "맥북", "갤럭시 버즈", "여행 경비", "카메라"]

def generate_spending_pattern():
    pattern = {}
    for cat in random.sample(CATEGORIES, random.randint(3, 6)):
        if cat == "카페":
            pattern[cat] = random.randint(20000, 80000)
        elif cat == "식비":
            pattern[cat] = random.randint(80000, 300000)
        elif cat == "교통":
            pattern[cat] = random.randint(30000, 80000)
        elif cat == "배달음식":
            pattern[cat] = random.randint(50000, 200000)
        else:
            pattern[cat] = random.randint(10000, 100000)
    return pattern

def generate_mission_from_pattern(pattern, daily_available, goal_item):
    top_category = max(pattern, key=pattern.get)
    top_amount = pattern[top_category]
    limit = int(top_amount * random.uniform(0.5, 0.8) / 30)
    limit = max(3000, (limit // 1000) * 1000)

    saving = int(daily_available * random.uniform(0.2, 0.5))
    saving = max(1000, (saving // 1000) * 1000)

    return {
        "restraint": {
            "categoryName": top_category,
            "limitAmount": limit,
            "contributionRate": round(random.uniform(0.5, 2.0), 1)
        },
        "saving": {
            "transferAmount": saving,
            "contributionRate": round(random.uniform(0.3, 1.5), 1)
        }
    }

def generate_mission_dataset(n=300):
    data = []
    for _ in range(n):
        pattern = generate_spending_pattern()
        total = sum(pattern.values())
        estimated_income = max(3000000, total + 600000)
        daily_available = (estimated_income - total) // 30
        goal_item = random.choice(GOAL_ITEMS)
        mission = generate_mission_from_pattern(pattern, daily_available, goal_item)

        pattern_str = ", ".join([f"{k}: {v}원" for k, v in pattern.items()])

        data.append({
            "prompt": f"""당신은 금융 미션 생성 AI입니다.
사용자의 소비 패턴을 분석해 절약 미션을 생성합니다.
반드시 아래 JSON 형식으로만 응답하세요.
{{
  "restraint": {{"categoryName": "카테고리명", "limitAmount": 숫자, "contributionRate": 소수}},
  "saving": {{"transferAmount": 숫자, "contributionRate": 소수}}
}}

소비 패턴: {pattern_str}
오늘 가용 금액: {daily_available}원
저축 목표: {goal_item}""",
            "completion": json.dumps(mission, ensure_ascii=False)
        })

    output_file = os.path.join(DATA_DIR, "mission_generation.jsonl")
    with open(output_file, "w", encoding="utf-8") as f:
        for item in data:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"미션 생성 데이터셋 생성 완료: {len(data)}건 → {output_file}")
    return data

if __name__ == "__main__":
    print("=== iM AgentiX 파인튜닝 데이터셋 생성 ===")
    goal_data = generate_goal_parsing_dataset(500)
    mission_data = generate_mission_dataset(350)
    print(f"\n총 {len(goal_data) + len(mission_data)}건 생성 완료")
    print("MLX 파인튜닝 명령어:")
    print(f"  mlx_lm.lora --model mlx-community/Qwen2.5-1.5B-Instruct-4bit --train --data {os.path.join(DATA_DIR, 'goal_parsing.jsonl')} --iters 500")
    print(f"  mlx_lm.lora --model mlx-community/Qwen2.5-1.5B-Instruct-4bit --train --data {os.path.join(DATA_DIR, 'mission_generation.jsonl')} --iters 300")
