#!/bin/bash

# 에러가 발생해도 스크립트 실행이 중단되지 않도록 함
set +e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( dirname "$SCRIPT_DIR" )"
LOG_FILE="$SCRIPT_DIR/pipeline.log"
REPORT_FILE="$SCRIPT_DIR/eval_report.md"

echo "=== iM AgentiX Fine-tuning & Evaluation Pipeline ===" | tee "$LOG_FILE"
echo "시작 시간: $(date)" | tee -a "$LOG_FILE"

# 1. 의존성 설치
echo "[1/7] 의존성 라이브러리 (mlx, mlx-lm, httpx) 설치 중..." | tee -a "$LOG_FILE"
python3.12 -m pip install --break-system-packages mlx mlx-lm httpx >> "$LOG_FILE" 2>&1
if [ $? -eq 0 ]; then
    echo "의존성 설치 완료" | tee -a "$LOG_FILE"
else
    echo "[경고] 라이브러리 설치 실패. 이미 설치되었을 수 있음. 계속 진행합니다." | tee -a "$LOG_FILE"
fi

# 2. 데이터셋 생성 및 분할
echo "[2/7] 데이터셋 생성 및 분할 실행 중..." | tee -a "$LOG_FILE"
python3.12 "$SCRIPT_DIR/generate_dataset.py" >> "$LOG_FILE" 2>&1
python3.12 "$SCRIPT_DIR/split_dataset.py" >> "$LOG_FILE" 2>&1
if [ -f "$SCRIPT_DIR/data/train.jsonl" ]; then
    echo "데이터셋 준비 완료 (Train: $(wc -l < "$SCRIPT_DIR/data/train.jsonl"), Valid: $(wc -l < "$SCRIPT_DIR/data/valid.jsonl"), Golden: $(wc -l < "$SCRIPT_DIR/data/golden_eval.jsonl"))" | tee -a "$LOG_FILE"
else
    echo "[에러] 데이터셋 분할 실패! 파일이 존재하지 않습니다." | tee -a "$LOG_FILE"
    exit 1
fi

# 기존 학습 결과 삭제 (깨끗한 학습 환경 조성)
rm -rf "$PROJECT_DIR/adapters" 2>/dev/null
rm -rf "$SCRIPT_DIR/fused_model" 2>/dev/null

# 3. MLX LoRA 학습
ITERS=600
if [ "$1" == "--dry-run" ]; then
    ITERS=2
    echo "[Dry Run Mode] 학습 반복 횟수를 2회로 축소하여 전체 파이프라인 흐름을 검증합니다." | tee -a "$LOG_FILE"
fi

echo "[3/7] MLX LoRA 파인튜닝 진행 중 (Qwen2.5-1.5B-Instruct-4bit, iters=$ITERS)..." | tee -a "$LOG_FILE"
python3.12 -m mlx_lm.lora \
  --model mlx-community/Qwen2.5-1.5B-Instruct-4bit \
  --train \
  --data "$SCRIPT_DIR/data/" \
  --iters $ITERS \
  --save-every 100 >> "$LOG_FILE" 2>&1


if [ -d "$PROJECT_DIR/adapters" ]; then
    echo "LoRA 파인튜닝 완료 (adapters 폴더 생성됨)" | tee -a "$LOG_FILE"
else
    echo "[경고] LoRA 파인튜닝 가중치 생성 실패! 로컬 MLX 베이스 모델로 평가를 진행합니다. 로그 파일($LOG_FILE)을 확인하세요." | tee -a "$LOG_FILE"
fi

# 4. 가중치 Fusing (병합)
if [ -d "$PROJECT_DIR/adapters" ]; then
    echo "[4/7] LoRA 가중치를 베이스 모델에 Fuse(병합) 중..." | tee -a "$LOG_FILE"
    python3.12 -m mlx_lm.fuse \
      --model mlx-community/Qwen2.5-1.5B-Instruct-4bit \
      --adapter-path "$PROJECT_DIR/adapters" \
      --save-path "$SCRIPT_DIR/fused_model" >> "$LOG_FILE" 2>&1
      
    if [ -d "$SCRIPT_DIR/fused_model" ]; then
        echo "가중치 병합 완료 (fused_model 폴더 생성됨)" | tee -a "$LOG_FILE"
    else
        echo "[경고] 가중치 병합 실패. Ollama 임포트는 건너뛰며, 로컬 MLX 어댑터 로드를 사용해 평가를 진행합니다." | tee -a "$LOG_FILE"
    fi
else
    echo "[4/7] 어댑터가 없어 병합 단계를 건너뜁니다." | tee -a "$LOG_FILE"
fi

# 5. Ollama 등록 시도
if [ -d "$SCRIPT_DIR/fused_model" ]; then
    echo "[5/7] Fused 모델을 Ollama에 qwen2.5-tuned 명칭으로 등록 시도 중..." | tee -a "$LOG_FILE"
    
    # Modelfile 생성
    echo "FROM $SCRIPT_DIR/fused_model" > "$SCRIPT_DIR/Modelfile"
    
    # Ollama create 실행
    ollama create qwen2.5-tuned -f "$SCRIPT_DIR/Modelfile" >> "$LOG_FILE" 2>&1
    
    if [ $? -eq 0 ]; then
        echo "Ollama 모델 등록 완료: qwen2.5-tuned" | tee -a "$LOG_FILE"
    else
        echo "[경고] Ollama 모델 등록 실패 (Ollama가 꺼져있거나 GGUF 변환을 수행하지 못함). 로컬 MLX 폴백을 사용해 평가를 진행합니다." | tee -a "$LOG_FILE"
    fi
else
    echo "[5/7] Fused 모델이 존재하지 않아 Ollama 모델 등록 단계를 건너뜁니다." | tee -a "$LOG_FILE"
fi

# 6. Before/After 평가
echo "[6/7] 골든 데이터셋 기반 Before/After 성능 평가 진행 중..." | tee -a "$LOG_FILE"
python3.12 "$SCRIPT_DIR/evaluate.py" >> "$LOG_FILE" 2>&1
if [ -f "$SCRIPT_DIR/eval_temp.md" ]; then
    echo "성능 평가 완료" | tee -a "$LOG_FILE"
else
    echo "[경고] 성능 평가 도중 에러가 발생했습니다." | tee -a "$LOG_FILE"
fi

# 7. 품질 테스트
echo "[7/7] 튜닝 모델 대상 미션 생성 50회 품질 분포 테스트 진행 중..." | tee -a "$LOG_FILE"
python3.12 "$SCRIPT_DIR/quality_test.py" >> "$LOG_FILE" 2>&1
if [ -f "$SCRIPT_DIR/quality_temp.md" ]; then
    echo "품질 분포 테스트 완료" | tee -a "$LOG_FILE"
else
    echo "[경고] 품질 분포 테스트 도중 에러가 발생했습니다." | tee -a "$LOG_FILE"
fi

# 최종 리포트 합성
echo "# iM AgentiX 새벽 자동화 파인튜닝 & 평가 최종 보고서" > "$REPORT_FILE"
echo "생성 일시: $(date)" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

if [ -f "$SCRIPT_DIR/eval_temp.md" ]; then
    cat "$SCRIPT_DIR/eval_temp.md" >> "$REPORT_FILE"
    rm "$SCRIPT_DIR/eval_temp.md"
else
    echo "## 2단계: Before/After 평가 결과" >> "$REPORT_FILE"
    echo "평가 데이터를 생성하지 못했습니다. pipeline.log를 참고하세요." >> "$REPORT_FILE"
fi

echo "" >> "$REPORT_FILE"

if [ -f "$SCRIPT_DIR/quality_temp.md" ]; then
    cat "$SCRIPT_DIR/quality_temp.md" >> "$REPORT_FILE"
    rm "$SCRIPT_DIR/quality_temp.md"
else
    echo "## 3단계: 미션 생성 품질 분포 테스트" >> "$REPORT_FILE"
    echo "품질 분포 테스트 데이터를 생성하지 못했습니다. pipeline.log를 참고하세요." >> "$REPORT_FILE"
fi

echo "" >> "$REPORT_FILE"
echo "=== 파이프라인 전체 과정 종료 ===" | tee -a "$LOG_FILE"
echo "최종 마크다운 보고서 저장 위치: $REPORT_FILE" | tee -a "$LOG_FILE"
