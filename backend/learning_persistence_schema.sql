-- Belief States Table for Persistent Learning Progress
CREATE TABLE IF NOT EXISTS belief_states (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    concept_id TEXT NOT NULL,
    belief_unknown REAL NOT NULL DEFAULT 0.8,
    belief_partial REAL NOT NULL DEFAULT 0.15,
    belief_mastered REAL NOT NULL DEFAULT 0.05,
    interaction_count INTEGER DEFAULT 0,
    last_interaction TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, concept_id)
);

CREATE INDEX IF NOT EXISTS idx_belief_user ON belief_states(user_id);
CREATE INDEX IF NOT EXISTS idx_belief_concept ON belief_states(concept_id);
CREATE INDEX IF NOT EXISTS idx_belief_user_concept ON belief_states(user_id, concept_id);

-- Interaction Events Table for Learning Analytics
CREATE TABLE IF NOT EXISTS interaction_events (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    card_id TEXT NOT NULL,
    concept_id TEXT NOT NULL,
    answer_index INTEGER NOT NULL,
    is_correct BOOLEAN NOT NULL,
    time_spent_seconds INTEGER NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_interaction_user ON interaction_events(user_id);
CREATE INDEX IF NOT EXISTS idx_interaction_concept ON interaction_events(concept_id);
CREATE INDEX IF NOT EXISTS idx_interaction_timestamp ON interaction_events(timestamp);
