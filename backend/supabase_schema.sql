-- Supabase Database Schema for Agentic Finance System
-- Run this in your Supabase SQL Editor

-- Transactions table (user-specific)
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL DEFAULT 'default_user',
    amount DECIMAL(12, 2) NOT NULL,
    merchant TEXT,
    category TEXT,
    currency TEXT DEFAULT 'INR',
    timestamp TIMESTAMPTZ NOT NULL,
    raw_message TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Risk logs table (user-specific)
CREATE TABLE IF NOT EXISTS risk_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL DEFAULT 'default_user',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_income DECIMAL(12, 2) NOT NULL,
    total_expenses DECIMAL(12, 2) NOT NULL,
    savings DECIMAL(12, 2) NOT NULL,
    heuristic_risk TEXT NOT NULL,
    ml_risk_level TEXT,
    ml_risk_confidence DECIMAL(5, 4)
);

-- User profiles table
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id TEXT PRIMARY KEY,
    display_name TEXT,
    email TEXT,
    photo_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON transactions(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(category);
CREATE INDEX IF NOT EXISTS idx_transactions_user_timestamp ON transactions(user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_risk_logs_user ON risk_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_risk_logs_created_at ON risk_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_risk_logs_dates ON risk_logs(start_date, end_date);

-- Enable Row Level Security (RLS)
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

-- RLS policies: users can only access their own data
CREATE POLICY "Users can manage own transactions" ON transactions
    FOR ALL USING (user_id = current_setting('request.jwt.claims', true)::json->>'sub');

CREATE POLICY "Users can manage own risk_logs" ON risk_logs
    FOR ALL USING (user_id = current_setting('request.jwt.claims', true)::json->>'sub');

CREATE POLICY "Users can manage own profile" ON user_profiles
    FOR ALL USING (user_id = current_setting('request.jwt.claims', true)::json->>'sub');

-- Service role bypass (for backend API calls with service key)
CREATE POLICY "Service role full access transactions" ON transactions
    FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Service role full access risk_logs" ON risk_logs
    FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Service role full access user_profiles" ON user_profiles
    FOR ALL USING (true) WITH CHECK (true);
