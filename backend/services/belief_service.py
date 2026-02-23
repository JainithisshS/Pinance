"""Belief state management and update logic."""
from datetime import datetime
from backend.models.learning import BeliefState


def create_default_belief(user_id: str, concept_id: str) -> BeliefState:
    """Create a default belief state for a new concept."""
    return BeliefState(
        user_id=user_id,
        concept_id=concept_id,
        belief_unknown=1.0,
        belief_partial=0.0,
        belief_mastered=0.0,
        interaction_count=0,
        last_updated=datetime.now()
    )


def update_belief(
    current_belief: BeliefState,
    is_correct: bool,
    time_spent_seconds: int
) -> BeliefState:
    """
    Update belief state after a quiz interaction using Bayesian-inspired logic.
    
    Args:
        current_belief: Current belief state
        is_correct: Whether the answer was correct
        time_spent_seconds: Time spent on the quiz
        
    Returns:
        Updated belief state with normalized probabilities
        
    Algorithm:
        - Correct answer → shift probability toward Mastered
        - Incorrect answer → shift toward Unknown/Partial
        - Fast correct (< 30s) → stronger Mastered signal
        - Slow incorrect (> 60s) → stronger Unknown signal
    """
    # Define transition probabilities based on performance
    if is_correct:
        if time_spent_seconds < 30:  # Fast correct - high confidence
            shift = {
                "mastered": +0.3,
                "partial": +0.1,
                "unknown": -0.4
            }
        else:  # Slow correct - moderate confidence
            shift = {
                "mastered": +0.15,
                "partial": +0.15,
                "unknown": -0.3
            }
    else:
        if time_spent_seconds > 60:  # Slow incorrect - struggling
            shift = {
                "unknown": +0.4,
                "partial": -0.2,
                "mastered": -0.2
            }
        else:  # Fast incorrect - likely guessing
            shift = {
                "unknown": +0.2,
                "partial": +0.1,
                "mastered": -0.3
            }
    
    # Apply shifts with bounds checking
    new_unknown = max(0.0, min(1.0, current_belief.belief_unknown + shift["unknown"]))
    new_partial = max(0.0, min(1.0, current_belief.belief_partial + shift["partial"]))
    new_mastered = max(0.0, min(1.0, current_belief.belief_mastered + shift["mastered"]))
    
    # Normalize to ensure sum = 1.0
    total = new_unknown + new_partial + new_mastered
    if total > 0:
        new_unknown /= total
        new_partial /= total
        new_mastered /= total
    else:
        # Fallback if all became 0
        new_unknown = 0.6
        new_partial = 0.3
        new_mastered = 0.1
    
    # Create updated belief state
    return BeliefState(
        user_id=current_belief.user_id,
        concept_id=current_belief.concept_id,
        belief_unknown=new_unknown,
        belief_partial=new_partial,
        belief_mastered=new_mastered,
        interaction_count=current_belief.interaction_count + 1,
        last_updated=datetime.now()
    )


def get_mastery_level(belief: BeliefState) -> str:
    """
    Get human-readable mastery level.
    
    Returns:
        "unknown", "partial", or "mastered"
    """
    if belief.belief_mastered > 0.6:
        return "mastered"
    elif belief.belief_partial > 0.5:
        return "partial"
    else:
        return "unknown"


def calculate_confidence(belief: BeliefState) -> float:
    """
    Calculate confidence in the belief state (entropy-based).
    
    Returns:
        Confidence score from 0.0 to 1.0
    """
    # Use max probability as confidence
    max_prob = max(belief.belief_unknown, belief.belief_partial, belief.belief_mastered)
    return max_prob
