"""Data models for adaptive micro-learning system."""
from datetime import datetime
from typing import Any, List, Dict, Optional
from pydantic import BaseModel, Field, validator


class Concept(BaseModel):
    """Represents a financial concept in the knowledge graph."""
    id: str
    name: str
    description: str
    prerequisites: List[str] = Field(default_factory=list)
    difficulty: int = Field(ge=1, le=5)
    estimated_time_minutes: int
    
    class Config:
        json_schema_extra = {
            "example": {
                "id": "budgeting_basics",
                "name": "Budgeting Basics",
                "description": "Understanding how to create and maintain a personal budget",
                "prerequisites": ["income_basics"],
                "difficulty": 2,
                "estimated_time_minutes": 10
            }
        }


class BeliefState(BaseModel):
    """Probabilistic belief state for user's knowledge of a concept."""
    user_id: str
    concept_id: str
    belief_unknown: float = Field(ge=0.0, le=1.0)
    belief_partial: float = Field(ge=0.0, le=1.0)
    belief_mastered: float = Field(ge=0.0, le=1.0)
    interaction_count: int = 0
    last_updated: datetime = Field(default_factory=datetime.now)
    
    @validator('belief_mastered')
    def validate_sum(cls, v, values):
        """Ensure probabilities sum to 1.0."""
        total = values.get('belief_unknown', 0) + values.get('belief_partial', 0) + v
        if abs(total - 1.0) > 0.001:
            raise ValueError(f"Belief probabilities must sum to 1.0, got {total}")
        return v
    
    class Config:
        json_schema_extra = {
            "example": {
                "user_id": "user_123",
                "concept_id": "budgeting_basics",
                "belief_unknown": 0.7,
                "belief_partial": 0.2,
                "belief_mastered": 0.1,
                "interaction_count": 2
            }
        }


class Quiz(BaseModel):
    """Quiz question for a learning card."""
    question: str
    options: List[str] = Field(min_length=4, max_length=4)
    correct_answer_index: int = Field(ge=0, le=3)
    explanation: str
    
    class Config:
        json_schema_extra = {
            "example": {
                "question": "What is the 50/30/20 budgeting rule?",
                "options": [
                    "50% needs, 30% wants, 20% savings",
                    "50% savings, 30% needs, 20% wants",
                    "50% wants, 30% savings, 20% needs",
                    "50% income, 30% expenses, 20% debt"
                ],
                "correct_answer_index": 0,
                "explanation": "The 50/30/20 rule allocates 50% to needs, 30% to wants, and 20% to savings."
            }
        }


class LearningCard(BaseModel):
    """A micro-learning card with content and quiz."""
    id: str
    concept_id: str
    content: str
    quiz: Quiz
    source: str = "grok"  # "grok" or "static"
    created_at: datetime = Field(default_factory=datetime.now)
    
    class Config:
        json_schema_extra = {
            "example": {
                "id": "card_123",
                "concept_id": "budgeting_basics",
                "content": "Budgeting is the process of creating a plan...",
                "quiz": {
                    "question": "What is the 50/30/20 rule?",
                    "options": ["...", "...", "...", "..."],
                    "correct_answer_index": 0,
                    "explanation": "..."
                },
                "source": "grok"
            }
        }


class InteractionEvent(BaseModel):
    """User interaction with a learning card."""
    id: str
    user_id: str
    card_id: str
    concept_id: str
    answer_index: int = Field(ge=0, le=3)
    is_correct: bool
    time_spent_seconds: int
    timestamp: datetime = Field(default_factory=datetime.now)


class CompilationTrace(BaseModel):
    """Trace of curriculum compilation for explainability."""
    id: str
    user_id: str
    selected_concept_id: str
    reason: str
    candidate_concepts: List[str]
    scores: Dict[str, float]
    timestamp: datetime = Field(default_factory=datetime.now)


class CardResponse(BaseModel):
    """Response containing a learning card and explanation."""
    card: LearningCard
    explanation: Dict[str, Any]


class SubmitAnswerRequest(BaseModel):
    """Request to submit a quiz answer."""
    card_id: str
    answer_index: int = Field(ge=0, le=3)
    time_spent_seconds: int


class SubmitAnswerResponse(BaseModel):
    """Response after submitting a quiz answer."""
    is_correct: bool
    explanation: str
    belief_update: Dict[str, Any]
    next_card_ready: bool
