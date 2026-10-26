-- ─────────────────────────────────────────────────────────────
-- 금융 AI 내부통제 & 컴플라이언스 테이블 정의 및 시드 데이터
-- ─────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS vector;

-- 1. 규정집 및 조항 벡터 테이블
CREATE TABLE IF NOT EXISTS compliance_rules (
    id                     BIGSERIAL PRIMARY KEY,
    law_name               VARCHAR(100) NOT NULL,      -- 예: 금융소비자보호법, 전자금융거래법
    article_no             VARCHAR(50)  NOT NULL,      -- 예: 제17조, 제19조, 제21조
    rule_type              VARCHAR(50)  NOT NULL,      -- SUITABILITY, DISCLOSURE, UNFAIR_SOLICITATION, PREPAID_PROTECTION, HUMAN_REVIEW
    title                  VARCHAR(200) NOT NULL,
    content                TEXT         NOT NULL,      -- 조문 및 가이드라인 상세 내용
    risk_level             VARCHAR(20)  DEFAULT 'LOW', -- LOW, MEDIUM, HIGH
    requires_human_review  BOOLEAN      DEFAULT FALSE, -- 담당자(상담원/PB) 확인 필수 여부
    embedding              vector(768),                -- nomic-embed-text 벡터 임베딩
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_compliance_rules_embedding
    ON compliance_rules USING hnsw (embedding vector_cosine_ops);

-- 2. 내부통제 감사 로그 (책무구조도 및 사후 감사 대비)
CREATE TABLE IF NOT EXISTS compliance_audit_logs (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL REFERENCES users(id),
    goal_id                BIGINT,
    action_type            VARCHAR(50)  NOT NULL,      -- PLAN_RECOMMENDATION, PRODUCT_RECOMMENDATION, MISSION_VALIDATION
    plan_type              VARCHAR(50),                -- CONSUMPTION, ASSET_ACCUMULATION
    input_text             TEXT,                       -- 고객 발화 또는 요청 컨텍스트
    recommended_product    VARCHAR(100),               -- 추천된 금융상품명 (해당 시)
    is_compliant           BOOLEAN      NOT NULL DEFAULT TRUE,
    risk_level             VARCHAR(20)  DEFAULT 'LOW',
    requires_human_review  BOOLEAN      NOT NULL DEFAULT FALSE,
    human_review_reason    TEXT,                       -- 담당자 연결/확인 사유
    matched_rules          TEXT,                       -- 적용된 법 조문 목록 (JSON or 텍스트)
    compliance_footnote    TEXT,                       -- 레포트 하단 '근거:' 문구
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_compliance_audit_logs_user_date
    ON compliance_audit_logs (user_id, created_at DESC);

-- ─────────────────────────────────────────────────────────────
-- 기본 핵심 법률 규정 Seed 데이터 (초기 투입)
-- ─────────────────────────────────────────────────────────────

INSERT INTO compliance_rules (law_name, article_no, rule_type, title, content, risk_level, requires_human_review) VALUES
(
    '금융소비자보호법', '제17조', 'SUITABILITY', '적합성의 원칙',
    '금융상품판매업자는 소비자의 재산상황, 소득수준, 가용예산 등에 비추어 적합하지 아니하다고 인정되는 금융상품 계약 체결을 권유해서는 아니 된다. 고객의 가처분소득 대비 과도한 월 저축액이나 무리한 상품 납입을 유도해서는 안 된다.',
    'HIGH', false
),
(
    '금융소비자보호법', '제18조', 'ADEQUACY', '적정성의 원칙',
    '소비자가 자발적으로 계약 체결을 원하는 경우에도 해당 상품이 소비자의 재산상황, 투자경험에 비추어 적정하지 아니한 경우 이를 고지하고 확인을 받아야 한다.',
    'MEDIUM', false
),
(
    '금융소비자보호법', '제19조', 'DISCLOSURE', '설명의무 이행',
    '금융상품 계약 체결을 권유할 때 상품의 기본구조, 최고/기본 금리 조건, 원금손실 위험, 예금자보호 여부, 중도해지 시 불이익을 소비자가 이해할 수 있도록 명확히 설명해야 하며 중요사항을 누락해서는 안 된다.',
    'HIGH', false
),
(
    '금융소비자보호법', '제21조', 'UNFAIR_SOLICITATION', '부당권유행위 금지',
    '불확실한 사항에 대하여 단정적 판단을 제공하거나 확실하다고 오인하게 할 소지가 있는 내용(무조건 100% 보장, 원금 손실 없는 등)을 알리는 행위를 금지한다.',
    'HIGH', false
),
(
    '전자금융거래법', '제19조 및 제21조의3', 'PREPAID_PROTECTION', '선불충전금(iMKRW) 100% 분리보관 및 환급 보장',
    '선불전자지급수단(iMKRW 토큰) 발행 시 충전금 총액의 100% 이상을 은행 등 공신력 있는 기관에 신탁/예치하여 고유재산과 분리보관해야 하며, 이용자가 환급을 청구할 경우 원화로 즉시 환급받을 수 있는 권리를 보장해야 한다.',
    'MEDIUM', false
),
(
    '금융회사지배구조법', '제24조 및 제24조의2', 'INTERNAL_CONTROL', '내부통제기준 및 감사추적(책무구조도)',
    '금융회사는 AI 상담 및 추천 과정에서 발생하는 불완전판매를 방지하기 위해 실시간 가드레일을 두고, 모든 검증 기록을 감사 로그(Audit Trail)로 영구 적재하여 사후 관리 책임을 다해야 한다.',
    'HIGH', false
),
(
    '자본시장법', '제18조', 'UNREGISTERED_ADVICE', '미등록 투자자문 및 일임 행위 금지',
    'AI는 특정 투자상품의 매매 타이밍을 단정적으로 유도하거나 고객 자산을 일임받아 임의 운용할 수 없으며, 단순 재무 계획 가이드 및 정보 제공에 한정해야 한다.',
    'HIGH', false
),
(
    '금융분야 AI 가이드라인', '제3원칙', 'HUMAN_REVIEW', '보조수단성 원칙 및 고위험 상품 전담 상담원 연계',
    'AI는 보조적 추천 수단에 불과하므로, 고난도 금융투자상품이나 고객 재정 대비 초고액 자산 형성 플랜, 복합 파생 상품의 경우 불완전판매 예방을 위해 반드시 전문 상담원(PB)의 1:1 확인 및 상담 연계를 거쳐야 한다.',
    'HIGH', true
),
(
    '금융분야 가명정보 가이드라인', '제32조', 'PII_MASKING', '고객 개인신용정보 전처리 마스킹',
    '고객과의 자연어 상담 데이터 중 계좌번호, 주민등록번호, 카드번호, 전화번호 등 식별 데이터는 외부 LLM 모델에 전달되기 전 반드시 사전 비식별화(가명/마스킹) 처리가 완료되어야 한다.',
    'HIGH', false
),
(
    '금융소비자보호 감독규정', '별표 3', 'DEPOSIT_PROTECTION', '예금자보호법 적용 고지',
    '예금성 금융상품을 추천할 때에는 「예금자보호법」에 따라 원금과 소정의 이자를 합하여 1인당 최고 5천만 원까지 보호된다는 사실을 반드시 고지해야 한다.',
    'LOW', false
)
ON CONFLICT DO NOTHING;
