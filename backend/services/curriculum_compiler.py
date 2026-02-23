"""Curriculum compiler service - selects the next best learning card."""
from typing import Dict, Set, Tuple, Optional, Any
from datetime import datetime
import uuid

from backend.models.learning import Concept, BeliefState, CompilationTrace
from backend.services.concept_service import ConceptGraph


def calculate_relevance(concept: Concept, context: Dict[str, Any]) -> float:
    """
    Calculate how relevant a concept is to the user's current financial situation.
    
    Args:
        concept: The concept to evaluate
        context: User context (risk_level, spending_trend, savings_rate, etc.)
        
    Returns:
        Relevance multiplier (1.0 = baseline, >1.0 = more relevant)
    """
    relevance = 1.0
    concept_id_lower = concept.id.lower()
    
    # High risk → prioritize budgeting, emergency funds
    if context.get("risk_level") == "high":
        if any(keyword in concept_id_lower for keyword in ["budget", "emergency", "expense"]):
            relevance *= 1.5
    
    # High spending → prioritize expense tracking and budgeting
    if context.get("spending_trend") == "increasing":
        if any(keyword in concept_id_lower for keyword in ["expense", "tracking", "budget"]):
            relevance *= 1.3
    
    # Low savings → prioritize saving strategies
    savings_rate = context.get("savings_rate", 0.5)
    if savings_rate < 0.2:
        if any(keyword in concept_id_lower for keyword in ["saving", "emergency"]):
            relevance *= 1.4
    
    # High debt → prioritize debt management
    if context.get("has_debt", False):
        if "debt" in concept_id_lower:
            relevance *= 1.6
    
    return relevance


def generate_explanation(
    selected_concept_id: str,
    concept_graph: ConceptGraph,
    scores: Dict[str, float],
    context: Dict[str, Any]
) -> str:
    """
    Generate human-readable explanation for why a concept was selected.
    
    Args:
        selected_concept_id: The selected concept ID
        concept_graph: The concept graph
        scores: Scores for all candidate concepts
        context: User context
        
    Returns:
        Human-readable explanation string
    """
    concept = concept_graph.get_concept(selected_concept_id)
    if not concept:
        return "This concept was selected for your learning path."
    
    reasons = []
    
    # Check if it's a foundation
    prereqs = concept_graph.get_prerequisites(selected_concept_id)
    if not prereqs:
        reasons.append("This is a foundational concept with no prerequisites")
    else:
        reasons.append("You've mastered the prerequisites for this concept")
    
    # Check relevance to context
    if context.get("risk_level") == "high" and any(k in concept.id for k in ["budget", "emergency"]):
        reasons.append("This is highly relevant to your current financial situation")
    
    if context.get("spending_trend") == "increasing" and "expense" in concept.id:
        reasons.append("This will help you manage your increasing expenses")
    
    # Difficulty
    if concept.difficulty <= 2:
        reasons.append("This is a beginner-friendly topic")
    elif concept.difficulty >= 4:
        reasons.append("This is an advanced concept that builds on your knowledge")
    
    explanation = f"{concept.name}: " + ", and ".join(reasons) + "."
    return explanation


def compile_next_card(
    user_id: str,
    concept_graph: ConceptGraph,
    belief_states: Dict[str, BeliefState],
    context: Optional[Dict[str, Any]] = None
) -> Tuple[str, CompilationTrace]:
    """
    Select the next best learning card for the user using greedy optimization.
    
    Scoring function:
        score = readiness × urgency × relevance
    
    Where:
        - readiness = minimum mastery of all prerequisites (0.0 to 1.0)
        - urgency = 1 - P(Mastered) (0.0 to 1.0)
        - relevance = context-based multiplier (≥1.0)
    
    Args:
        user_id: The user ID
        concept_graph: The concept dependency graph
        belief_states: Dictionary of concept_id -> BeliefState
        context: Optional context signals (risk, spending, etc.)
        
    Returns:
        Tuple of (selected_concept_id, compilation_trace)
    """
    if context is None:
        context = {}
    
    candidate_scores = {}
    mastered_concepts = set()
    
    # Identify mastered concepts
    for concept_id, belief in belief_states.items():
        if belief.belief_mastered > 0.7:  # Threshold for "mastered"
            mastered_concepts.add(concept_id)
    
    # Score each concept
    for concept_id, concept in concept_graph.concepts.items():
        # Get or create belief state
        belief = belief_states.get(concept_id)
        if belief is None:
            # Default: completely unknown
            belief = BeliefState(
                user_id=user_id,
                concept_id=concept_id,
                belief_unknown=1.0,
                belief_partial=0.0,
                belief_mastered=0.0,
                interaction_count=0
            )
        
        # Skip if already mastered
        if belief.belief_mastered > 0.8:
            continue
        
        # Calculate readiness (prerequisites mastery)
        prereqs = concept_graph.get_prerequisites(concept_id)
        if prereqs:
            prereq_mastery_scores = []
            for prereq_id in prereqs:
                prereq_belief = belief_states.get(prereq_id)
                if prereq_belief is None:
                    prereq_mastery_scores.append(0.0)  # Prerequisite not started
                else:
                    prereq_mastery_scores.append(prereq_belief.belief_mastered)
            
            # Readiness = minimum mastery of all prerequisites
            readiness = min(prereq_mastery_scores)
        else:
            # No prerequisites = always ready
            readiness = 1.0
        
        # Skip if not ready (prerequisites not mastered)
        if readiness < 0.6:  # Threshold for "ready"
            continue
        
        # Calculate urgency (how much they need to learn this)
        urgency = 1.0 - belief.belief_mastered
        
        # Calculate relevance (context-based)
        relevance = calculate_relevance(concept, context)
        
        # Final score
        score = readiness * urgency * relevance
        candidate_scores[concept_id] = score
    
    # If no candidates, return a foundation concept
    if not candidate_scores:
        # Find concepts with no prerequisites
        foundation_concepts = [
            cid for cid, c in concept_graph.concepts.items()
            if not concept_graph.get_prerequisites(cid)
        ]
        if foundation_concepts:
            selected_concept_id = foundation_concepts[0]
            candidate_scores[selected_concept_id] = 1.0
        else:
            # Fallback: return first concept
            selected_concept_id = list(concept_graph.concepts.keys())[0]
            candidate_scores[selected_concept_id] = 1.0
    else:
        # Select highest scoring concept
        selected_concept_id = max(candidate_scores, key=candidate_scores.get)
    
    # Generate explanation
    reason = generate_explanation(
        selected_concept_id,
        concept_graph,
        candidate_scores,
        context
    )
    
    # Create compilation trace for explainability
    trace = CompilationTrace(
        id=str(uuid.uuid4()),
        user_id=user_id,
        selected_concept_id=selected_concept_id,
        reason=reason,
        candidate_concepts=list(candidate_scores.keys()),
        scores=candidate_scores,
        timestamp=datetime.now()
    )
    
    return selected_concept_id, trace


def get_user_context(user_id: str) -> Dict[str, Any]:
    """
    Get user context from their financial data.
    
    This would typically query the transactions and analysis endpoints.
    For now, returns a placeholder.
    
    Args:
        user_id: The user ID
        
    Returns:
        Context dictionary with risk_level, spending_trend, savings_rate, etc.
    """
    # TODO: Integrate with existing finance analysis
    # For now, return neutral context
    return {
        "risk_level": "medium",
        "spending_trend": "stable",
        "savings_rate": 0.3,
        "has_debt": False
    }
