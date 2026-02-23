-- Adaptive Learning System Schema for Supabase

-- ============================================================================
-- CONCEPTS AND DEPENDENCIES
-- ============================================================================

CREATE TABLE IF NOT EXISTS concepts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    difficulty INTEGER CHECK (difficulty BETWEEN 1 AND 5),
    estimated_time_minutes INTEGER,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS concept_prerequisites (
    concept_id TEXT REFERENCES concepts(id) ON DELETE CASCADE,
    prerequisite_id TEXT REFERENCES concepts(id) ON DELETE CASCADE,
    PRIMARY KEY (concept_id, prerequisite_id)
);

-- Index for faster prerequisite lookups
CREATE INDEX IF NOT EXISTS idx_concept_prereqs_concept ON concept_prerequisites(concept_id);
CREATE INDEX IF NOT EXISTS idx_concept_prereqs_prereq ON concept_prerequisites(prerequisite_id);

-- ============================================================================
-- BELIEF STATES (User Knowledge Tracking)
-- ============================================================================

CREATE TABLE IF NOT EXISTS belief_states (
    user_id TEXT NOT NULL,
    concept_id TEXT REFERENCES concepts(id) ON DELETE CASCADE,
    belief_unknown FLOAT CHECK (belief_unknown BETWEEN 0 AND 1),
    belief_partial FLOAT CHECK (belief_partial BETWEEN 0 AND 1),
    belief_mastered FLOAT CHECK (belief_mastered BETWEEN 0 AND 1),
    interaction_count INTEGER DEFAULT 0,
    last_updated TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, concept_id),
    CONSTRAINT belief_sum CHECK (
        ABS((belief_unknown + belief_partial + belief_mastered) - 1.0) < 0.01
    )
);

-- Indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_belief_states_user ON belief_states(user_id);
CREATE INDEX IF NOT EXISTS idx_belief_states_mastery ON belief_states(belief_mastered DESC);

-- ============================================================================
-- LEARNING CARDS (Content + Quizzes)
-- ============================================================================

CREATE TABLE IF NOT EXISTS learning_cards (
    id TEXT PRIMARY KEY,
    concept_id TEXT REFERENCES concepts(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    quiz_question TEXT NOT NULL,
    quiz_options JSONB NOT NULL,  -- Array of 4 options
    quiz_correct_index INTEGER CHECK (quiz_correct_index BETWEEN 0 AND 3),
    quiz_explanation TEXT,
    source TEXT DEFAULT 'grok',  -- 'grok' or 'static'
    created_at TIMESTAMP DEFAULT NOW()
);

-- Index for faster concept lookups
CREATE INDEX IF NOT EXISTS idx_learning_cards_concept ON learning_cards(concept_id);

-- ============================================================================
-- INTERACTION EVENTS (User Quiz Attempts)
-- ============================================================================

CREATE TABLE IF NOT EXISTS interaction_events (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    card_id TEXT REFERENCES learning_cards(id) ON DELETE SET NULL,
    concept_id TEXT REFERENCES concepts(id) ON DELETE CASCADE,
    answer_index INTEGER,
    is_correct BOOLEAN,
    time_spent_seconds INTEGER,
    timestamp TIMESTAMP DEFAULT NOW()
);

-- Indexes for analytics
CREATE INDEX IF NOT EXISTS idx_interaction_events_user ON interaction_events(user_id);
CREATE INDEX IF NOT EXISTS idx_interaction_events_concept ON interaction_events(concept_id);
CREATE INDEX IF NOT EXISTS idx_interaction_events_timestamp ON interaction_events(timestamp DESC);

-- ============================================================================
-- COMPILATION TRACES (Explainability)
-- ============================================================================

CREATE TABLE IF NOT EXISTS compilation_traces (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    selected_concept_id TEXT REFERENCES concepts(id) ON DELETE SET NULL,
    reason TEXT,
    candidate_concepts JSONB,  -- Array of concept IDs
    scores JSONB,  -- Object mapping concept_id to score
    timestamp TIMESTAMP DEFAULT NOW()
);

-- Index for user trace history
CREATE INDEX IF NOT EXISTS idx_compilation_traces_user ON compilation_traces(user_id);
CREATE INDEX IF NOT EXISTS idx_compilation_traces_timestamp ON compilation_traces(timestamp DESC);

-- ============================================================================
-- SEED DATA: Core Financial Concepts
-- ============================================================================

-- Insert core concepts
INSERT INTO concepts (id, name, description, difficulty, estimated_time_minutes) VALUES
    ('money_basics', 'Money Basics', 'Understanding what money is and its role in daily life', 1, 5),
    ('income_basics', 'Income Basics', 'Understanding different sources of income', 1, 5),
    ('expense_tracking', 'Expense Tracking', 'How to track and categorize your spending', 2, 8),
    ('budgeting_basics', 'Budgeting Basics', 'Creating and maintaining a personal budget', 2, 10),
    ('emergency_fund', 'Emergency Fund', 'Building and maintaining an emergency savings fund', 3, 10),
    ('debt_management', 'Debt Management', 'Understanding and managing different types of debt', 3, 12),
    ('saving_strategies', 'Saving Strategies', 'Effective strategies for building savings', 3, 10),
    ('investment_basics', 'Investment Basics', 'Introduction to investing and growing wealth', 4, 15),
    ('credit_score', 'Credit Score', 'Understanding credit scores and how to improve them', 4, 12),
    ('financial_goals', 'Financial Goal Setting', 'Setting and achieving short and long-term financial goals', 4, 12)
ON CONFLICT (id) DO NOTHING;

-- Insert prerequisites (DAG edges)
INSERT INTO concept_prerequisites (concept_id, prerequisite_id) VALUES
    ('expense_tracking', 'money_basics'),
    ('budgeting_basics', 'income_basics'),
    ('budgeting_basics', 'expense_tracking'),
    ('emergency_fund', 'budgeting_basics'),
    ('debt_management', 'budgeting_basics'),
    ('saving_strategies', 'budgeting_basics'),
    ('investment_basics', 'emergency_fund'),
    ('investment_basics', 'saving_strategies'),
    ('credit_score', 'debt_management'),
    ('financial_goals', 'budgeting_basics'),
    ('financial_goals', 'saving_strategies')
ON CONFLICT DO NOTHING;
