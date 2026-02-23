"""API router for adaptive micro-learning system."""
from fastapi import APIRouter, HTTPException, Depends, BackgroundTasks
from typing import Dict, Optional
import uuid
import time
import asyncio
from datetime import datetime
from backend.auth import get_current_user

from backend.models.learning import (
    CardResponse,
    SubmitAnswerRequest,
    SubmitAnswerResponse,
    LearningCard,
    BeliefState,
    InteractionEvent
)
from backend.services.concept_service import get_concept_graph
from backend.services.curriculum_compiler import compile_next_card, get_user_context
from backend.services.belief_service import update_belief, create_default_belief, get_mastery_level
from backend.services.groq_client import get_groq_client
from backend.learning_db import save_belief_state_db, load_belief_states_db, save_interaction_event_db
# from backend.db import get_supabase  # Unused and caused circular import crash

router = APIRouter(prefix="/api/learning", tags=["Adaptive Learning"])


# In-memory storage for development (replace with Supabase queries in production)
_belief_states: Dict[str, Dict[str, BeliefState]] = {}  # user_id -> {concept_id -> BeliefState}
_learning_cards: Dict[str, LearningCard] = {}  # concept_id -> LearningCard (concept cache, cleared for fresh content)
_served_cards: Dict[str, LearningCard] = {}    # card_id -> LearningCard (persists for submit-answer lookup)


async def get_user_belief_states(user_id: str) -> Dict[str, BeliefState]:
    """Get all belief states for a user (cached after first DB load)."""
    # Return in-memory cache if already populated for this user
    if user_id in _belief_states and _belief_states[user_id]:
        return _belief_states[user_id]
    
    # First access: load from database
    db_beliefs = await load_belief_states_db(user_id)
    _belief_states[user_id] = db_beliefs
    return _belief_states[user_id]


async def save_belief_state(belief: BeliefState) -> None:
    """Save a belief state."""
    # Save to database
    await save_belief_state_db(belief)
    
    # Update in-memory cache
    if belief.user_id not in _belief_states:
        _belief_states[belief.user_id] = {}
    _belief_states[belief.user_id][belief.concept_id] = belief


async def get_or_create_card(concept_id: str) -> LearningCard:
    """Get existing card or generate new one with Grok AI."""
    # Check cache
    if concept_id in _learning_cards:
        print(f"[Learning] Cache HIT for '{concept_id}'")
        return _learning_cards[concept_id]
    
    print(f"[Learning] Cache MISS for '{concept_id}', generating with Groq...")
    
    # TODO: Check Supabase first
    
    # Generate new card with Groq
    concept_graph = get_concept_graph()
    concept = concept_graph.get_concept(concept_id)
    
    if not concept:
        raise HTTPException(status_code=404, detail=f"Concept '{concept_id}' not found")
    
    groq = get_groq_client()
    content, quiz = await groq.generate_card(concept)
    
    card = LearningCard(
        id=str(uuid.uuid4()),
        concept_id=concept_id,
        content=content,
        quiz=quiz,
        source="groq",
        created_at=datetime.now()
    )
    
    # Cache by concept (cleared for fresh content next visit)
    _learning_cards[concept_id] = card
    # Also store by card ID so submit-answer can always find it
    _served_cards[card.id] = card
    
    # TODO: Save to Supabase
    
    return card


@router.get("/next-card", response_model=CardResponse)
async def get_next_card(
    user_id: str = Depends(get_current_user),
    exclude: str = ""  # comma-separated concept IDs to skip (recently shown)
):
    """
    Get the next learning card for the user.
    Accepts ?exclude=concept1,concept2 to skip recently-shown concepts.
    """
    try:
        t0 = time.time()
        exclude_set = set(e.strip() for e in exclude.split(",") if e.strip())

        # Get user's current knowledge state
        belief_states = await get_user_belief_states(user_id)
        context = get_user_context(user_id)
        concept_graph = get_concept_graph()

        # Try to find a concept not in the exclude list
        selected_concept_id = None
        trace = None
        all_concept_ids = list(concept_graph.concepts.keys())

        for attempt in range(len(all_concept_ids) + 1):
            sid, tr = compile_next_card(
                user_id=user_id,
                concept_graph=concept_graph,
                belief_states=belief_states,
                context=context
            )
            if sid not in exclude_set:
                selected_concept_id = sid
                trace = tr
                break
            # Temporarily mark this concept as mastered in the LOCAL copy
            # so compile_next_card skips it on the next iteration.
            # Use valid probabilities that sum to 1.0.
            temp = belief_states.get(sid) or create_default_belief(user_id, sid)
            from backend.models.learning import BeliefState as BS
            belief_states[sid] = BS(
                user_id=temp.user_id,
                concept_id=temp.concept_id,
                belief_unknown=0.05,
                belief_partial=0.0,
                belief_mastered=0.95,  # sums to 1.0 — pretend mastered so compiler skips it
                interaction_count=temp.interaction_count
            )

        if not selected_concept_id:
            # All concepts excluded — pick any not in exclude, or just the first
            remaining = [c for c in all_concept_ids if c not in exclude_set]
            selected_concept_id = remaining[0] if remaining else all_concept_ids[0]
            trace = type('T', (), {'reason': 'Cycling through all concepts', 'scores': {}})()  # dummy trace

        print(f"[Learning] Selected concept: '{selected_concept_id}' (excluded: {exclude_set}) in {time.time()-t0:.2f}s")

        # Use cached card if available (fast); background pre-generation keeps cards fresh
        card = await get_or_create_card(selected_concept_id)

        # Reload real belief (not the temp boosted one)
        real_beliefs = await get_user_belief_states(user_id)
        belief = real_beliefs.get(selected_concept_id) or create_default_belief(user_id, selected_concept_id)

        concept = concept_graph.get_concept(selected_concept_id)
        explanation = {
            "why_selected": getattr(trace, 'reason', 'Selected for your learning path'),
            "readiness": 1.0,
            "urgency": 1.0 - belief.belief_mastered,
            "relevance": getattr(trace, 'scores', {}).get(selected_concept_id, 1.0),
            "mastery_level": get_mastery_level(belief),
            "interaction_count": belief.interaction_count,
            "mastery_percent": round(belief.belief_mastered * 100, 1),
            "difficulty": concept.difficulty if concept else 1,
            "concept_name": concept.name if concept else selected_concept_id
        }

        print(f"[Learning] /next-card responded in {time.time()-t0:.2f}s")
        return CardResponse(card=card, explanation=explanation)

    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Error generating card: {str(e)}")


async def _pregenerate_next_card(user_id: str):
    """Background task: pre-generate the next learning card so it's cached."""
    try:
        belief_states = await get_user_belief_states(user_id)
        context = get_user_context(user_id)
        concept_graph = get_concept_graph()
        selected_concept_id, _ = compile_next_card(
            user_id=user_id,
            concept_graph=concept_graph,
            belief_states=belief_states,
            context=context
        )
        await get_or_create_card(selected_concept_id)
        print(f"[Learning] Pre-generated card for next concept: '{selected_concept_id}'")
    except Exception as e:
        print(f"[Learning] Pre-generation failed: {e}")


@router.post("/submit-answer", response_model=SubmitAnswerResponse)
async def submit_answer(
    request: SubmitAnswerRequest,
    user_id: str = Depends(get_current_user)
):
    """
    Submit a quiz answer and update belief state.
    
    This endpoint:
    1. Validates the answer
    2. Updates belief state using Bayesian logic
    3. Records interaction event
    4. Triggers curriculum recompilation
    5. Returns feedback and belief update
    """
    try:
        # Find the card — check served_cards first (persists even after concept cache cleared)
        card = _served_cards.get(request.card_id)
        if not card:
            # Fallback: search concept cache
            for cached_card in _learning_cards.values():
                if cached_card.id == request.card_id:
                    card = cached_card
                    break
        
        if not card:
            raise HTTPException(status_code=404, detail="Card not found")
        
        # Check answer
        is_correct = request.answer_index == card.quiz.correct_answer_index
        
        # Get current belief state
        belief_states = await get_user_belief_states(user_id)
        current_belief = belief_states.get(card.concept_id)
        
        if not current_belief:
            current_belief = create_default_belief(user_id, card.concept_id)
        
        # Update belief state
        new_belief = update_belief(
            current_belief=current_belief,
            is_correct=is_correct,
            time_spent_seconds=request.time_spent_seconds
        )
        
        # If mastery level changed, invalidate card cache so next visit gets level-appropriate content
        old_level = get_mastery_level(current_belief)
        new_level = get_mastery_level(new_belief)
        if old_level != new_level:
            _learning_cards.pop(card.concept_id, None)
            print(f"[Learning] Mastery level changed ({old_level} -> {new_level}) — card cache cleared for '{card.concept_id}'")
        
        # Save updated belief
        await save_belief_state(new_belief)
        
        # Record interaction event
        event = InteractionEvent(
            id=str(uuid.uuid4()),
            user_id=user_id,
            card_id=card.id,
            concept_id=card.concept_id,
            answer_index=request.answer_index,
            is_correct=is_correct,
            time_spent_seconds=request.time_spent_seconds,
            timestamp=datetime.now()
        )
        # Save event to database
        await save_interaction_event_db(event)
        
        # Build response
        mastery_change = new_belief.belief_mastered - current_belief.belief_mastered
        
        response = SubmitAnswerResponse(
            is_correct=is_correct,
            explanation=card.quiz.explanation if is_correct else f"Not quite. {card.quiz.explanation}",
            belief_update={
                "concept_id": card.concept_id,
                "previous_mastery": round(current_belief.belief_mastered, 2),
                "new_mastery": round(new_belief.belief_mastered, 2),
                "change": f"{'+'  if mastery_change >= 0 else ''}{round(mastery_change, 2)}",
                "mastery_level": get_mastery_level(new_belief)
            },
            next_card_ready=True
        )
        
        # Pre-generate the next card in background so it's ready when user taps Next
        asyncio.ensure_future(_pregenerate_next_card(user_id))
        
        return response
    
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Error processing answer: {str(e)}")



@router.get("/progress")
async def get_progress(user_id: str = Depends(get_current_user)):
    """
    Get user's learning progress across all concepts.
    
    Returns mastery levels, interaction counts, and overall progress.
    """
    try:
        belief_states = await get_user_belief_states(user_id)
        concept_graph = get_concept_graph()
        
        progress = []
        total_mastery = 0.0
        
        for concept_id, concept in concept_graph.concepts.items():
            belief = belief_states.get(concept_id)
            if not belief:
                belief = create_default_belief(user_id, concept_id)
            
            progress.append({
                "concept_id": concept_id,
                "concept_name": concept.name,
                "mastery_level": get_mastery_level(belief),
                "mastery_score": round(belief.belief_mastered, 2),
                "interaction_count": belief.interaction_count,
                "difficulty": concept.difficulty
            })
            
            total_mastery += belief.belief_mastered
        
        overall_progress = total_mastery / len(concept_graph.concepts) if concept_graph.concepts else 0.0
        
        return {
            "user_id": user_id,
            "overall_progress": round(overall_progress, 2),
            "concepts": progress,
            "total_concepts": len(concept_graph.concepts),
            "mastered_count": sum(1 for p in progress if p["mastery_level"] == "mastered")
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error fetching progress: {str(e)}")


@router.get("/explanation")
async def get_explanation(
    concept_id: str,
    user_id: str = Depends(get_current_user)
):
    """
    Get detailed explanation for why a concept was selected or its current state.
    """
    try:
        concept_graph = get_concept_graph()
        concept = concept_graph.get_concept(concept_id)
        
        if not concept:
            raise HTTPException(status_code=404, detail="Concept not found")
        
        belief_states = await get_user_belief_states(user_id)
        belief = belief_states.get(concept_id)
        
        if not belief:
            belief = create_default_belief(user_id, concept_id)
        
        # Get prerequisites status
        prereqs = concept_graph.get_prerequisites(concept_id)
        prereq_status = []
        for prereq_id in prereqs:
            prereq_concept = concept_graph.get_concept(prereq_id)
            prereq_belief = belief_states.get(prereq_id)
            if not prereq_belief:
                prereq_belief = create_default_belief(user_id, prereq_id)
            
            prereq_status.append({
                "concept": prereq_concept.name if prereq_concept else prereq_id,
                "mastered": prereq_belief.belief_mastered > 0.7
            })
        
        return {
            "concept_id": concept_id,
            "concept_name": concept.name,
            "description": concept.description,
            "difficulty": concept.difficulty,
            "belief_state": {
                "unknown": round(belief.belief_unknown, 2),
                "partial": round(belief.belief_partial, 2),
                "mastered": round(belief.belief_mastered, 2)
            },
            "mastery_level": get_mastery_level(belief),
            "interaction_count": belief.interaction_count,
            "prerequisites_status": prereq_status,
            "has_prerequisites": len(prereqs) > 0
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error fetching explanation: {str(e)}")
