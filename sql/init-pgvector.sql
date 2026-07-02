CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    monthly_income BIGINT DEFAULT 0,
    fixed_expense BIGINT DEFAULT 0,
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
