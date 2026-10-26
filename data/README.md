# 시뮬레이션 데이터 안내

이 폴더의 CSV는 실험 재현을 위한 입력 및 파생 데이터입니다. 실제 고객 거래 내역이나 서비스 실증 데이터로 해석하지 않습니다.

| 파일 | 생성 방식 및 용도 |
|---|---|
| `raw/worknet_wage_table.csv` | `scripts/prepare_raw_data.py`에 정의된 직업별 임금 기준표 |
| `raw/nemotron_personas_korea.csv` | 난수로 생성한 연령·직업·지역 페르소나. Nemotron 원본 다운로드 파일이 아닙니다. |
| `raw/uci_credit_card_clients.csv` | 난수 분포와 로지스틱 식으로 생성한 카드 한도·부채·연체 라벨. UCI 원본 다운로드 파일이 아닙니다. |
| `processed/cohort_1000.csv` | 위 입력을 결합하고 소득·한도소진율·목표 등을 부여한 합성 코호트 1,000명 |
| `processed/mission_catalog.json` | `simulation/mission_catalog.py`의 미션 카탈로그에서 내보낸 데이터 |

`scripts/prepare_raw_data.py`와 `simulation/generate_cohort_dataset.py`가 데이터 생성 과정을 정의합니다. `simulation/financial_impact_models.py`의 출처 주석과 `results/simulation_output.json`의 메타데이터는 모델 가정의 기록이며, 외부 원문 검증을 대신하지 않습니다.

`results/validation_report.md`의 “실측 EAD”는 저장된 합성 코호트의 신용한도를 합산했다는 의미로 읽어야 합니다. 실제 금융기관 포트폴리오를 측정한 값이 아닙니다.

`compliance/`는 규정 검색 데모의 입력 폴더입니다. 입력 형식과 적재 방법은 [별도 안내](compliance/README.md)를 참고하세요.
