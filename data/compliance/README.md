# 🏛️ 금융 AI 컴플라이언스 규정집 투입 폴더

여기에 금융 관련 규정집 파일들을 넣어주시면 됩니다.

## 지원 파일 형식
- **PDF 파일** (`.pdf`): 금융위 가이드라인, 금융보안원 보안안내서, 법령집 등
- **텍스트 / 마크다운 파일** (`.txt`, `.md`): 법 조문, 내부통제 지침 등
- **JSON 파일** (`.json`): 정제된 규정/조항 데이터셋

## 자동 인제스트 방법
파일을 이 폴더(`data/compliance/`)에 넣은 후, 아래 명령어를 실행하면 자동으로 조항별 청킹 및 벡터화(`nomic-embed-text`)되어 PostgreSQL `compliance_rules` 테이블에 적재됩니다:

```bash
python3 scripts/ingest_compliance.py
```
