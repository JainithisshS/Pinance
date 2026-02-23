from __future__ import annotations

from typing import Dict, List, Literal, Optional

from fastapi import APIRouter
from pydantic import BaseModel

from ..learning.curriculum_engine import (
    Belief,
    KnowledgeState,
    Observation,
    compile_plan,
    initial_beliefs,
    load_concepts,
    simulate_content,
    update_belief,
)


router = APIRouter()


class BeliefStateModel(BaseModel):
    unknown: float
    partial: float
    mastered: float


class ConceptBeliefModel(BaseModel):
    beliefs: Dict[str, BeliefStateModel]


class PlanItemModel(BaseModel):
    concept_id: str
    concept_name: str
    action: Literal["Read", "Simulate", "Example"]
    reason: str
    priority: float
    content_snippet: str
    card_title: str
    learning_text: str
    quiz_question: str
    quiz_options: List[str]
    quiz_correct: int


class CurriculumPlanResponse(BaseModel):
    plan: List[PlanItemModel]
    beliefs: Dict[str, BeliefStateModel]
    compiler_log: List[str]


class ObservationModel(BaseModel):
    concept_id: str
    observation: Observation


class CurriculumUpdateRequest(BaseModel):
    beliefs: Dict[str, BeliefStateModel]
    observation: ObservationModel


@router.get("/curriculum/plan", response_model=CurriculumPlanResponse)
async def get_initial_plan() -> CurriculumPlanResponse:
    concepts = load_concepts()
    beliefs = initial_beliefs(concepts)

    plan_items, log = compile_plan(concepts, beliefs)

    belief_out = {
        cid: BeliefStateModel(unknown=b.unknown, partial=b.partial, mastered=b.mastered)
        for cid, b in beliefs.items()
    }

    plan_out: List[PlanItemModel] = []
    for item in plan_items:
        concept = concepts[item.concept_id]
        plan_out.append(
            PlanItemModel(
                concept_id=item.concept_id,
                concept_name=item.concept_name,
                action=item.action,
                reason=item.reason,
                priority=item.priority,
                content_snippet=simulate_content(concept),
                card_title=concept.card_title,
                learning_text=concept.learning_text,
                quiz_question=concept.quiz_question,
                quiz_options=concept.quiz_options or [],
                quiz_correct=concept.quiz_correct,
            )
        )

    return CurriculumPlanResponse(plan=plan_out, beliefs=belief_out, compiler_log=log)


@router.post("/curriculum/update", response_model=CurriculumPlanResponse)
async def update_curriculum(payload: CurriculumUpdateRequest) -> CurriculumPlanResponse:
    concepts = load_concepts()

    # Rebuild Belief objects from incoming JSON
    beliefs: Dict[str, Belief] = {}
    for cid, b in payload.beliefs.items():
        beliefs[cid] = Belief(unknown=b.unknown, partial=b.partial, mastered=b.mastered)

    # Apply observation
    obs = payload.observation
    if obs.concept_id in beliefs:
        beliefs[obs.concept_id] = update_belief(beliefs[obs.concept_id], obs.observation)

    plan_items, log = compile_plan(concepts, beliefs)

    belief_out = {
        cid: BeliefStateModel(unknown=b.unknown, partial=b.partial, mastered=b.mastered)
        for cid, b in beliefs.items()
    }

    plan_out: List[PlanItemModel] = []
    for item in plan_items:
        concept = concepts[item.concept_id]
        plan_out.append(
            PlanItemModel(
                concept_id=item.concept_id,
                concept_name=item.concept_name,
                action=item.action,
                reason=item.reason,
                priority=item.priority,
                content_snippet=simulate_content(concept),
                card_title=concept.card_title,
                learning_text=concept.learning_text,
                quiz_question=concept.quiz_question,
                quiz_options=concept.quiz_options or [],
                quiz_correct=concept.quiz_correct,
            )
        )

    # Prepend explanation of what changed
    before_after = f"Applied observation {obs.observation.value} to {obs.concept_id}."
    log.insert(0, before_after)

    return CurriculumPlanResponse(plan=plan_out, beliefs=belief_out, compiler_log=log)
