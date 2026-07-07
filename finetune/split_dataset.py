import json
import os
import random

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")

def load_jsonl(file_path):
    data = []
    with open(file_path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                data.append(json.loads(line))
    return data

def save_jsonl(data, file_path):
    with open(file_path, "w", encoding="utf-8") as f:
        for item in data:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

def split_dataset():
    goal_parsing_path = os.path.join(DATA_DIR, "goal_parsing.jsonl")
    mission_gen_path = os.path.join(DATA_DIR, "mission_generation.jsonl")

    # 원본 파일이 없을 시 데이터셋 재생성 시도
    if not os.path.exists(goal_parsing_path) or not os.path.exists(mission_gen_path):
        print("원본 데이터셋 파일이 존재하지 않습니다. generate_dataset.py를 실행합니다...")
        import generate_dataset
        generate_dataset.generate_goal_parsing_dataset(500)
        generate_dataset.generate_mission_dataset(350)

    goal_data = load_jsonl(goal_parsing_path)
    mission_data = load_jsonl(mission_gen_path)

    # 데이터 순서 무작위화
    random.seed(42)
    random.shuffle(goal_data)
    random.shuffle(mission_data)

    # 파싱 500건: train 400 + valid 50 + golden 50
    goal_train = goal_data[0:400]
    goal_valid = goal_data[400:450]
    goal_golden = goal_data[450:500]

    # 미션 300(실제 350건 생성): train 250 + valid 50 + golden 50
    if len(mission_data) < 350:
        print(f"미션 데이터 개수({len(mission_data)})가 분할 요구량(350)보다 부족합니다. 재생성합니다...")
        import generate_dataset
        mission_data = generate_dataset.generate_mission_dataset(350)
        random.shuffle(mission_data)

    mission_train = mission_data[0:250]
    mission_valid = mission_data[250:300]
    mission_golden = mission_data[300:350]

    # 병합
    train_set = goal_train + mission_train
    valid_set = goal_valid + mission_valid
    golden_set = goal_golden + mission_golden

    # 무작위 셔플
    random.shuffle(train_set)
    random.shuffle(valid_set)
    random.shuffle(golden_set)

    # 저장
    save_jsonl(train_set, os.path.join(DATA_DIR, "train.jsonl"))
    save_jsonl(valid_set, os.path.join(DATA_DIR, "valid.jsonl"))
    save_jsonl(golden_set, os.path.join(DATA_DIR, "golden_eval.jsonl"))

    print("=== 데이터셋 분할 완료 ===")
    print(f"훈련 데이터 (train.jsonl): {len(train_set)}건 (목표 파싱 400 + 미션 생성 250)")
    print(f"검증 데이터 (valid.jsonl): {len(valid_set)}건 (목표 파싱 50 + 미션 생성 50)")
    print(f"골든 평가 데이터 (golden_eval.jsonl): {len(golden_set)}건 (목표 파싱 50 + 미션 생성 50)")

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=str, default=None, help="Path to input jsonl file to split")
    args = parser.parse_args()

    if args.input:
        input_path = args.input
        if not os.path.isabs(input_path):
            # Try SCRIPT_DIR then DATA_DIR
            candidate = os.path.join(SCRIPT_DIR, input_path)
            if os.path.exists(candidate):
                input_path = candidate
            else:
                input_path = os.path.join(DATA_DIR, input_path)
        
        print(f"커스텀 입력 데이터셋 분할 시작: {input_path}")
        data = load_jsonl(input_path)
        
        random.seed(42)
        random.shuffle(data)
        
        n = len(data)
        train_size = int(n * 0.8)
        valid_size = int(n * 0.1)
        
        train_set = data[:train_size]
        valid_set = data[train_size:train_size+valid_size]
        golden_set = data[train_size+valid_size:]
        
        save_jsonl(train_set, os.path.join(DATA_DIR, "train.jsonl"))
        save_jsonl(valid_set, os.path.join(DATA_DIR, "valid.jsonl"))
        save_jsonl(golden_set, os.path.join(DATA_DIR, "golden_eval.jsonl"))
        
        print("=== 커스텀 데이터셋 분할 완료 ===")
        print(f"훈련 데이터 (train.jsonl): {len(train_set)}건")
        print(f"검증 데이터 (valid.jsonl): {len(valid_set)}건")
        print(f"골든 평가 데이터 (golden_eval.jsonl): {len(golden_set)}건")
    else:
        split_dataset()
