CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    monthly_income BIGINT DEFAULT 0,
    fixed_expense BIGINT DEFAULT 0,
    income_quintile SMALLINT DEFAULT 3,
    disposable_income BIGINT DEFAULT 0,
    daily_available BIGINT DEFAULT 0,
    fcm_token VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    slot_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    item_image_url VARCHAR(500),
    target_amount BIGINT NOT NULL,
    current_amount BIGINT NOT NULL DEFAULT 0,
    achievement_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    registration_type VARCHAR(50) NOT NULL,
    daily_budget BIGINT,
    duration_days INT,
    estimated_completion_date DATE,
    withdrawal_condition JSONB,
    lock_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS missions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    goal_id BIGINT REFERENCES goals(id),
    mission_source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    category VARCHAR(50),
    description VARCHAR(500),
    limit_amount BIGINT,
    deposit_amount BIGINT,
    duration_days INT,
    start_date DATE,
    end_date DATE,
    overage_rate DOUBLE PRECISION,
    proposal_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS deposits (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL REFERENCES missions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount BIGINT NOT NULL,
    imkrw_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    lock_tx_hash VARCHAR(200),
    release_tx_hash VARCHAR(200),
    locked_at TIMESTAMP,
    released_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    merchant VARCHAR(255),
    amount BIGINT NOT NULL,
    category VARCHAR(50),
    transacted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS my_data_caches (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    category_expenses JSONB NOT NULL DEFAULT '{}',
    weekly_avg_expenses JSONB NOT NULL DEFAULT '{}',
    daily_available_amount BIGINT NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS spending_embeddings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    merchant_name VARCHAR(255),
    amount BIGINT NOT NULL,
    embedding vector(768),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mission_cases (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    mission_type VARCHAR(20) NOT NULL,
    limit_amount BIGINT,
    success_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    embedding vector(768),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goals_user_id ON goals(user_id);
CREATE INDEX IF NOT EXISTS idx_missions_user_id ON missions(user_id);
CREATE INDEX IF NOT EXISTS idx_missions_goal_id ON missions(goal_id);
CREATE INDEX IF NOT EXISTS idx_missions_status ON missions(status);
CREATE INDEX IF NOT EXISTS idx_deposits_mission_id ON deposits(mission_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_transacted_at ON transactions(transacted_at);

CREATE TABLE IF NOT EXISTS mydata_card_approvals (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    approved_num    VARCHAR(50)  NOT NULL,
    approved_dtime  TIMESTAMP    NOT NULL,
    status          VARCHAR(2)   NOT NULL DEFAULT '01',
    merchant_name   VARCHAR(100) NOT NULL,
    approved_amt    BIGINT       NOT NULL,
    category        VARCHAR(20),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    CONSTRAINT uq_card_approval UNIQUE (user_id, approved_num)
);

CREATE INDEX IF NOT EXISTS idx_mydata_card_user_date
    ON mydata_card_approvals (user_id, approved_dtime DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_mydata_card_category
    ON mydata_card_approvals (user_id, category, approved_dtime DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS mydata_account_deposits (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id),
    trans_dtime    TIMESTAMP    NOT NULL,
    trans_type     VARCHAR(2)   NOT NULL,
    trans_amt      BIGINT       NOT NULL,
    trans_memo     VARCHAR(200),
    after_balance  BIGINT,
    is_salary      BOOLEAN      DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mydata_deposits_user_date
    ON mydata_account_deposits (user_id, trans_dtime DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS merchant_category_mapping (
    id            BIGSERIAL    PRIMARY KEY,
    merchant_name VARCHAR(100) NOT NULL UNIQUE,
    category      VARCHAR(20)  NOT NULL,
    source        VARCHAR(100) DEFAULT '우리카드/BC카드 업종 분류 기준 (2024)'
);

INSERT INTO merchant_category_mapping (merchant_name, category) VALUES
('스타벅스','CAFE'),('이디야커피','CAFE'),('메가커피','CAFE'),
('투썸플레이스','CAFE'),('할리스커피','CAFE'),('빽다방','CAFE'),
('컴포즈커피','CAFE'),('드롭탑','CAFE'),('엔제리너스','CAFE'),
('배달의민족','DELIVERY'),('쿠팡이츠','DELIVERY'),('요기요','DELIVERY'),
('맥도날드','FOOD'),('버거킹','FOOD'),('롯데리아','FOOD'),
('KFC','FOOD'),('서브웨이','FOOD'),
('CU','CONVENIENCE'),('GS25','CONVENIENCE'),('세븐일레븐','CONVENIENCE'),
('이마트24','CONVENIENCE'),('미니스톱','CONVENIENCE'),('씨유','CONVENIENCE'),
('이마트','GROCERY'),('홈플러스','GROCERY'),('롯데마트','GROCERY'),
('마켓컬리','GROCERY'),('코스트코','GROCERY'),('트레이더스','GROCERY'),
('무신사','SHOPPING'),('쿠팡','SHOPPING'),('지그재그','SHOPPING'),
('에이블리','SHOPPING'),('네이버쇼핑','SHOPPING'),('다이소','SHOPPING'),
('나이키','SHOPPING'),('아디다스','SHOPPING'),('유니클로','SHOPPING'),
('서울교통공사','TRANSPORT'),('카카오택시','TRANSPORT'),('티머니','TRANSPORT'),
('현대오일뱅크','TRANSPORT'),('GS칼텍스','TRANSPORT'),('코레일','TRANSPORT'),
('CGV','ENTERTAINMENT'),('메가박스','ENTERTAINMENT'),('롯데시네마','ENTERTAINMENT'),
('넷플릭스','ENTERTAINMENT'),('왓챠','ENTERTAINMENT'),('티빙','ENTERTAINMENT'),
('멜론','ENTERTAINMENT'),('스포티파이','ENTERTAINMENT')
ON CONFLICT (merchant_name) DO NOTHING;
