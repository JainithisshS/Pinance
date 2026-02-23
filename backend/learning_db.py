"""Database functions for persistent learning progress.
Supports Supabase (primary) with SQLite fallback."""
import os
import sqlite3
import json
from datetime import datetime
from typing import Dict, Optional, List
from pathlib import Path
from backend.models.learning import BeliefState, InteractionEvent

DB_PATH = Path(__file__).parent / "transactions.db"

# Check if Supabase is configured
USE_SUPABASE = bool(os.getenv("SUPABASE_URL"))
if USE_SUPABASE:
    try:
        from backend.supabase_client import get_supabase
        print("[LearningDB] Supabase mode enabled")
    except Exception as e:
        print(f"[LearningDB] Supabase import failed: {e}. Falling back to SQLite")
        USE_SUPABASE = False


def get_connection() -> sqlite3.Connection:
    """Get SQLite connection."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


async def save_belief_state_db(belief: BeliefState) -> None:
    """Save belief state to database (Supabase or SQLite)."""
    if USE_SUPABASE:
        try:
            supabase = get_supabase()
            data = {
                "user_id": belief.user_id,
                "concept_id": belief.concept_id,
                "belief_unknown": belief.belief_unknown,
                "belief_partial": belief.belief_partial,
                "belief_mastered": belief.belief_mastered,
                "interaction_count": belief.interaction_count,
                "last_updated": datetime.now().isoformat()
            }
            supabase.table("belief_states").upsert(
                data, on_conflict="user_id,concept_id"
            ).execute()
            print(f"[DB] Saved belief state to Supabase for user={belief.user_id}, concept={belief.concept_id}")
            return
        except Exception as e:
            print(f"[DB] Supabase belief save failed: {e}. Falling back to SQLite")

    # SQLite fallback
    conn = get_connection()
    try:
        conn.execute(
            """
            INSERT OR REPLACE INTO belief_states (
                id, user_id, concept_id, belief_unknown, belief_partial, 
                belief_mastered, interaction_count, last_interaction, 
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 
                COALESCE((SELECT created_at FROM belief_states WHERE user_id = ? AND concept_id = ?), ?),
                ?
            )
            """,
            (
                f"{belief.user_id}_{belief.concept_id}",
                belief.user_id,
                belief.concept_id,
                belief.belief_unknown,
                belief.belief_partial,
                belief.belief_mastered,
                belief.interaction_count,
                datetime.now().isoformat(),
                belief.user_id,
                belief.concept_id,
                datetime.now().isoformat(),
                datetime.now().isoformat()
            )
        )
        conn.commit()
        print(f"[DB] Saved belief state for user={belief.user_id}, concept={belief.concept_id}")
    finally:
        conn.close()


async def load_belief_states_db(user_id: str) -> Dict[str, BeliefState]:
    """Load all belief states for a user from database (Supabase or SQLite)."""
    if USE_SUPABASE:
        try:
            supabase = get_supabase()
            result = supabase.table("belief_states").select("*").eq("user_id", user_id).execute()
            belief_states = {}
            for row in result.data:
                belief = BeliefState(
                    user_id=row["user_id"],
                    concept_id=row["concept_id"],
                    belief_unknown=row["belief_unknown"],
                    belief_partial=row["belief_partial"],
                    belief_mastered=row["belief_mastered"],
                    interaction_count=row.get("interaction_count", 0)
                )
                belief_states[row["concept_id"]] = belief
            print(f"[DB] Loaded {len(belief_states)} belief states from Supabase for user={user_id}")
            return belief_states
        except Exception as e:
            print(f"[DB] Supabase belief load failed: {e}. Falling back to SQLite")

    # SQLite fallback
    conn = get_connection()
    try:
        cursor = conn.execute(
            """
            SELECT user_id, concept_id, belief_unknown, belief_partial, 
                   belief_mastered, interaction_count
            FROM belief_states
            WHERE user_id = ?
            """,
            (user_id,)
        )
        
        belief_states = {}
        for row in cursor.fetchall():
            belief = BeliefState(
                user_id=row["user_id"],
                concept_id=row["concept_id"],
                belief_unknown=row["belief_unknown"],
                belief_partial=row["belief_partial"],
                belief_mastered=row["belief_mastered"],
                interaction_count=row["interaction_count"]
            )
            belief_states[row["concept_id"]] = belief
        
        print(f"[DB] Loaded {len(belief_states)} belief states for user={user_id}")
        return belief_states
    finally:
        conn.close()


async def save_interaction_event_db(event: InteractionEvent) -> None:
    """Save interaction event to database (Supabase or SQLite)."""
    if USE_SUPABASE:
        try:
            supabase = get_supabase()
            data = {
                "id": event.id,
                "user_id": event.user_id,
                "card_id": event.card_id,
                "concept_id": event.concept_id,
                "answer_index": event.answer_index,
                "is_correct": event.is_correct,
                "time_spent_seconds": event.time_spent_seconds,
                "timestamp": event.timestamp.isoformat()
            }
            supabase.table("interaction_events").insert(data).execute()
            print(f"[DB] Saved interaction event to Supabase for user={event.user_id}, concept={event.concept_id}")
            return
        except Exception as e:
            print(f"[DB] Supabase interaction save failed: {e}. Falling back to SQLite")

    # SQLite fallback
    conn = get_connection()
    try:
        conn.execute(
            """
            INSERT INTO interaction_events (
                id, user_id, card_id, concept_id, answer_index, 
                is_correct, time_spent_seconds, timestamp
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                event.id,
                event.user_id,
                event.card_id,
                event.concept_id,
                event.answer_index,
                event.is_correct,
                event.time_spent_seconds,
                event.timestamp.isoformat()
            )
        )
        conn.commit()
        print(f"[DB] Saved interaction event for user={event.user_id}, concept={event.concept_id}")
    finally:
        conn.close()


async def get_user_stats_db(user_id: str) -> Dict:
    """Get learning statistics for a user (Supabase or SQLite)."""
    if USE_SUPABASE:
        try:
            supabase = get_supabase()
            # Get total interactions
            events = supabase.table("interaction_events").select("is_correct").eq("user_id", user_id).execute()
            total_interactions = len(events.data)
            correct_answers = sum(1 for e in events.data if e.get("is_correct"))

            # Get mastered concepts
            beliefs = supabase.table("belief_states").select("belief_mastered").eq("user_id", user_id).execute()
            mastered_concepts = sum(1 for b in beliefs.data if b.get("belief_mastered", 0) > 0.7)

            return {
                "total_interactions": total_interactions,
                "correct_answers": correct_answers,
                "accuracy": correct_answers / total_interactions if total_interactions > 0 else 0,
                "mastered_concepts": mastered_concepts
            }
        except Exception as e:
            print(f"[DB] Supabase stats failed: {e}. Falling back to SQLite")

    # SQLite fallback
    conn = get_connection()
    try:
        # Get total interactions
        cursor = conn.execute(
            "SELECT COUNT(*) as count FROM interaction_events WHERE user_id = ?",
            (user_id,)
        )
        total_interactions = cursor.fetchone()["count"]
        
        # Get correct answers
        cursor = conn.execute(
            "SELECT COUNT(*) as count FROM interaction_events WHERE user_id = ? AND is_correct = 1",
            (user_id,)
        )
        correct_answers = cursor.fetchone()["count"]
        
        # Get mastered concepts
        cursor = conn.execute(
            "SELECT COUNT(*) as count FROM belief_states WHERE user_id = ? AND belief_mastered > 0.7",
            (user_id,)
        )
        mastered_concepts = cursor.fetchone()["count"]
        
        return {
            "total_interactions": total_interactions,
            "correct_answers": correct_answers,
            "accuracy": correct_answers / total_interactions if total_interactions > 0 else 0,
            "mastered_concepts": mastered_concepts
        }
    finally:
        conn.close()
