#!/usr/bin/env python3
"""
ingest_compliance.py
- data/compliance/ 디렉토리 내의 규정집 파일(PDF, TXT, MD, JSON)을 탐색
- 조항 단위로 청킹(Chunking)
- Ollama(nomic-embed-text)로 768차원 벡터 임베딩 생성
- PostgreSQL compliance_rules 테이블에 자동 적재
"""

import os
import sys
import glob
import json
import re
import logging

logging.basicConfig(level=logging.INFO, format="[%(asctime)s] %(levelname)s: %(message)s")
logger = logging.getLogger(__name__)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
COMPLIANCE_DIR = os.path.join(PROJECT_DIR, "data", "compliance")

OLLAMA_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "agentic")
DB_USER = os.getenv("DB_USER", "agentic_user")
DB_PASS = os.getenv("DB_PASS", "agentic_password")

def extract_text_from_pdf(filepath: str) -> str:
    try:
        import pypdf
        reader = pypdf.PdfReader(filepath)
        text = ""
        for page in reader.pages:
            t = page.extract_text()
            if t:
                text += t + "\n"
        return text
    except ImportError:
        logger.warning(f"pypdf가 설치되어 있지 않습니다. pip install pypdf 후 재실행 권장.")
        return ""
    except Exception as e:
        logger.error(f"PDF 파싱 실패 ({filepath}): {e}")
        return ""

def split_into_articles(text: str, default_law_name: str) -> list[dict]:
    """법 조항(제O조) 패턴으로 텍스트 분할"""
    pattern = re.compile(r"(제\s*\d+\s*조(?:\s*의\s*\d+)?\s*\([^)]+\))")
    splits = pattern.split(text)

    rules = []
    if len(splits) <= 1:
        # 조문 패턴이 없는 일반 가이드라인의 경우 문단 단위로 쪼갬
        paragraphs = [p.strip() for p in text.split("\n\n") if len(p.strip()) > 40]
        for idx, p in enumerate(paragraphs):
            rules.append({
                "law_name": default_law_name,
                "article_no": f"항목 {idx+1}",
                "rule_type": "GUIDELINE",
                "title": p[:40].replace("\n", " ") + "...",
                "content": p,
                "risk_level": "MEDIUM",
                "requires_human_review": "고난도" in p or "고위험" in p or "상담원" in p
            })
        return rules

    for i in range(1, len(splits), 2):
        header = splits[i].strip()
        body = splits[i+1].strip() if i+1 < len(splits) else ""

        # article_no & title 분리: "제17조(적합성의 원칙)"
        m = re.match(r"(제\s*\d+\s*조(?:\s*의\s*\d+)?)\s*\(([^)]+)\)", header)
        art_no = m.group(1).replace(" ", "") if m else header
        title = m.group(2).strip() if m else header

        content = f"{header}\n{body}".strip()
        if len(content) < 20:
            continue

        rules.append({
            "law_name": default_law_name,
            "article_no": art_no,
            "rule_type": "STATUTE",
            "title": title,
            "content": content[:2000], # 최대 2000자
            "risk_level": "HIGH" if "금지" in title or "처벌" in title else "MEDIUM",
            "requires_human_review": "상담원" in content or "고난도" in content
        })
    return rules

def get_embedding(text: str) -> list[float]:
    import urllib.request
    url = f"{OLLAMA_URL}/api/embeddings"
    req_data = json.dumps({"model": "nomic-embed-text", "prompt": text}).encode("utf-8")
    req = urllib.request.Request(url, data=req_data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data.get("embedding", [])
    except Exception as e:
        logger.warning(f"Ollama 임베딩 생성 실패 (Ollama 실행 여부 확인): {e}")
        return []

def main():
    logger.info(f"=== 금융 규정집 자동 인제스트 시작 ===")
    logger.info(f"대상 폴더: {COMPLIANCE_DIR}")

    if not os.path.exists(COMPLIANCE_DIR):
        os.makedirs(COMPLIANCE_DIR, exist_ok=True)

    files = glob.glob(os.path.join(COMPLIANCE_DIR, "*"))
    files = [f for f in files if os.path.isfile(f) and not f.endswith("README.md")]

    if not files:
        logger.info(f"투입된 규정집 파일이 없습니다. 기본 시드 데이터를 유지합니다.")
        return

    all_rules = []
    for f in files:
        filename = os.path.basename(f)
        law_name = os.path.splitext(filename)[0]
        logger.info(f"파일 처리 중: {filename}")

        if f.endswith(".pdf"):
            raw_text = extract_text_from_pdf(f)
            rules = split_into_articles(raw_text, law_name)
            all_rules.extend(rules)
        elif f.endswith(".txt") or f.endswith(".md"):
            with open(f, "r", encoding="utf-8") as fp:
                raw_text = fp.read()
            rules = split_into_articles(raw_text, law_name)
            all_rules.extend(rules)
        elif f.endswith(".json"):
            with open(f, "r", encoding="utf-8") as fp:
                data = json.load(fp)
            if isinstance(data, list):
                all_rules.extend(data)

    logger.info(f"총 {len(all_rules)}개의 규정 조항 추출 완료. 벡터 임베딩 생성 중...")

    # DB 적재
    try:
        import psycopg2
        conn = psycopg2.connect(
            host=DB_HOST, port=DB_PORT,
            dbname=DB_NAME, user=DB_USER, password=DB_PASS
        )
        cur = conn.cursor()

        # 테이블 존재 확인
        cur.execute("""
            CREATE TABLE IF NOT EXISTS compliance_rules (
                id BIGSERIAL PRIMARY KEY,
                law_name VARCHAR(100) NOT NULL,
                article_no VARCHAR(50) NOT NULL,
                rule_type VARCHAR(50) NOT NULL,
                title VARCHAR(200) NOT NULL,
                content TEXT NOT NULL,
                risk_level VARCHAR(20) DEFAULT 'LOW',
                requires_human_review BOOLEAN DEFAULT FALSE,
                embedding vector(768),
                created_at TIMESTAMP DEFAULT NOW(),
                updated_at TIMESTAMP DEFAULT NOW()
            );
        """)
        conn.commit()

        inserted_count = 0
        for r in all_rules:
            emb = get_embedding(f"{r['law_name']} {r['article_no']} {r['title']} {r['content']}")
            cur.execute("""
                INSERT INTO compliance_rules (law_name, article_no, rule_type, title, content, risk_level, requires_human_review, embedding, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s::vector, NOW())
            """, (
                r.get("law_name", "금융규정"),
                r.get("article_no", "조항"),
                r.get("rule_type", "STATUTE"),
                r.get("title", "규정"),
                r.get("content", ""),
                r.get("risk_level", "MEDIUM"),
                r.get("requires_human_review", False),
                str(emb) if emb else None
            ))
            inserted_count += 1

        conn.commit()
        conn.close()
        logger.info(f"성공적으로 {inserted_count}개의 규정이 PostgreSQL compliance_rules 테이블에 적재되었습니다!")

    except Exception as e:
        logger.error(f"DB 직접 연결 실패: {e}")
        # fallback: SQL 파일로 저장
        sql_out = os.path.join(PROJECT_DIR, "sql", "ingested_compliance_rules.sql")
        with open(sql_out, "w", encoding="utf-8") as out_fp:
            for r in all_rules:
                content_escaped = r.get('content', '').replace("'", "''")
                out_fp.write(f"INSERT INTO compliance_rules (law_name, article_no, rule_type, title, content, risk_level, requires_human_review) "
                             f"VALUES ('{r.get('law_name')}', '{r.get('article_no')}', '{r.get('rule_type')}', '{r.get('title')}', '{content_escaped}', '{r.get('risk_level')}', {r.get('requires_human_review', False)});\n")
        logger.info(f"대체 수단: SQL 파일로 내보내기 완료 -> {sql_out}")

if __name__ == "__main__":
    main()
