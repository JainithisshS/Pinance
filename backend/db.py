import sqlite3
import os
from pathlib import Path
from typing import Any, Dict, Optional

# Load environment variables from .env file in backend directory
from dotenv import load_dotenv
env_path = Path(__file__).parent / ".env"
load_dotenv(dotenv_path=env_path)

# Check if Supabase is configured
USE_SUPABASE = bool(os.getenv("SUPABASE_URL"))

if USE_SUPABASE:
    try:
        from .supabase_client import get_supabase
        print("[DB] Using Supabase database")
    except Exception as e:
        print(f"[DB] Supabase import failed: {e}. Falling back to SQLite")
        USE_SUPABASE = False
else:
    print("[DB] Using SQLite database (local file)")

DB_PATH = Path(__file__).parent / "transactions.db"


def get_connection() -> sqlite3.Connection:
    """Get SQLite connection (used when Supabase is not configured)."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = get_connection()
    try:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL DEFAULT 'default_user',
                amount REAL NOT NULL,
                merchant TEXT,
                category TEXT,
                currency TEXT,
                timestamp TEXT NOT NULL,
                raw_message TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS risk_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL DEFAULT 'default_user',
                created_at TEXT NOT NULL,
                start_date TEXT NOT NULL,
                end_date TEXT NOT NULL,
                total_income REAL NOT NULL,
                total_expenses REAL NOT NULL,
                savings REAL NOT NULL,
                heuristic_risk TEXT NOT NULL,
                ml_risk_level TEXT,
                ml_risk_confidence REAL
            )
            """
        )
        conn.execute(
            """
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
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS interaction_events (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                card_id TEXT NOT NULL,
                concept_id TEXT NOT NULL,
                answer_index INTEGER NOT NULL,
                is_correct BOOLEAN NOT NULL,
                time_spent_seconds INTEGER NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_risk_logs_user ON risk_logs(user_id)")
        conn.commit()
    finally:
        conn.close()


def insert_transaction(data: Dict[str, Any]) -> int:
    """Insert transaction into database (Supabase or SQLite).
    
    Returns:
        int: The ID of the inserted transaction
    """
    if USE_SUPABASE:
        try:
            supabase = get_supabase()
            result = supabase.table("transactions").insert(data).execute()
            return result.data[0]["id"]
        except Exception as e:
            print(f"[DB] Supabase insert failed: {e}. Falling back to SQLite")
            # Fall through to SQLite
    
    # SQLite fallback
    conn = get_connection()
    try:
        cursor = conn.execute(
            """
            INSERT INTO transactions (user_id, amount, merchant, category, currency, timestamp, raw_message)
            VALUES (:user_id, :amount, :merchant, :category, :currency, :timestamp, :raw_message)
            """,
            data,
        )
        conn.commit()
        return cursor.lastrowid
    finally:
        conn.close()


def insert_risk_log(data: Dict[str, Any]) -> None:
    """Persist a single risk evaluation for offline analysis.

    This is used by Agent A to record both heuristic and ML-based
    risk outputs for later evaluation and tuning.
    """
    if USE_SUPABASE:
        try:
            supabase = get_supabase()
            supabase.table("risk_logs").insert(data).execute()
            return
        except Exception as e:
            print(f"[DB] Supabase risk_log insert failed: {e}. Falling back to SQLite")
            # Fall through to SQLite

    # SQLite fallback
    conn = get_connection()
    try:
        conn.execute(
            """
            INSERT INTO risk_logs (
                user_id,
                created_at,
                start_date,
                end_date,
                total_income,
                total_expenses,
                savings,
                heuristic_risk,
                ml_risk_level,
                ml_risk_confidence
            )
            VALUES (
                :user_id,
                :created_at,
                :start_date,
                :end_date,
                :total_income,
                :total_expenses,
                :savings,
                :heuristic_risk,
                :ml_risk_level,
                :ml_risk_confidence
            )
            """,
            data,
        )
        conn.commit()
    finally:
        conn.close()
