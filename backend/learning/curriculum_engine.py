from __future__ import annotations

"""Curriculum compiler + concept graph utilities for the learning tab.

This keeps everything small and deterministic for a student project:
- concepts.json defines ~8 concepts and dependencies (a DAG).
- belief state tracks Unknown/Partial/Mastered for each concept.
- the compiler turns belief + graph into a short learning plan.
- a simple Bayesian-style update adjusts beliefs after observations.
"""

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Dict, List, Literal, Tuple

import json

BASE_DIR = Path(__file__).parent
CONCEPTS_PATH = BASE_DIR / "concepts.json"

KnowledgeState = Literal["Unknown", "Partial", "Mastered"]


@dataclass
class Concept:
  id: str
  name: str
  description: str
  prerequisites: List[str]
  impact: int  # 1-4, higher = more important
  card_title: str = ""
  learning_text: str = ""
  quiz_question: str = ""
  quiz_options: List[str] = None  # type: ignore
  quiz_correct: int = 0  # index of correct option


@dataclass
class Belief:
  unknown: float
  partial: float
  mastered: float

  def normalised(self) -> "Belief":
    total = self.unknown + self.partial + self.mastered
    if total <= 0:
      return Belief(unknown=0.6, partial=0.3, mastered=0.1)
    return Belief(
      unknown=self.unknown / total,
      partial=self.partial / total,
      mastered=self.mastered / total,
    )


@dataclass
class PlanItem:
  concept_id: str
  concept_name: str
  action: Literal["Read", "Simulate", "Example"]
  reason: str
  priority: float


def load_concepts() -> Dict[str, Concept]:
  data = json.loads(CONCEPTS_PATH.read_text(encoding="utf-8"))
  concepts: Dict[str, Concept] = {}
  for row in data:
    c = Concept(
      id=row["id"],
      name=row["name"],
      description=row.get("description", ""),
      prerequisites=row.get("prerequisites", []),
      impact=int(row.get("impact", 2)),
      card_title=row.get("card_title", row["name"]),
      learning_text=row.get("learning_text", row.get("description", "")),
      quiz_question=row.get("quiz_question", ""),
      quiz_options=row.get("quiz_options", []),
      quiz_correct=int(row.get("quiz_correct", 0)),
    )
    concepts[c.id] = c
  _validate_dag(concepts)
  return concepts


def _validate_dag(concepts: Dict[str, Concept]) -> None:
  visiting = set()
  visited = set()

  def dfs(cid: str) -> None:
    if cid in visited:
      return
    if cid in visiting:
      raise ValueError("Cycle detected in concept graph")
    visiting.add(cid)
    for pre in concepts[cid].prerequisites:
      if pre in concepts:
        dfs(pre)
    visiting.remove(cid)
    visited.add(cid)

  for cid in concepts:
    dfs(cid)


def initial_beliefs(concepts: Dict[str, Concept]) -> Dict[str, Belief]:
  # Slightly uncertain prior; can be tuned per concept later
  return {
    cid: Belief(unknown=0.5, partial=0.3, mastered=0.2) for cid in concepts
  }


class Observation(Enum):
  QUIZ_CORRECT = "quiz_correct"
  QUIZ_WRONG = "quiz_wrong"
  SIMULATION_GOOD = "simulation_good"
  SIMULATION_POOR = "simulation_poor"


def update_belief(belief: Belief, obs: Observation) -> Belief:
  """Small hand-crafted Bayesian-style update.

  We do not compute true posteriors; instead we nudge the
  Unknown/Partial/Mastered weights in the right direction.
  """

  u, p, m = belief.unknown, belief.partial, belief.mastered

  if obs == Observation.QUIZ_CORRECT or obs == Observation.SIMULATION_GOOD:
    m += 0.2
    p += 0.1
    u -= 0.2
  elif obs == Observation.QUIZ_WRONG or obs == Observation.SIMULATION_POOR:
    u += 0.2
    p += 0.1
    m -= 0.2

  # Clamp and renormalise
  u = max(0.0, u)
  p = max(0.0, p)
  m = max(0.0, m)
  return Belief(u, p, m).normalised()


def _mastery_score(b: Belief) -> float:
  # Higher = more mastered
  nb = b.normalised()
  return nb.mastered + 0.5 * nb.partial


def compile_plan(
  concepts: Dict[str, Concept],
  beliefs: Dict[str, Belief],
  top_k: int = 4,
) -> Tuple[List[PlanItem], List[str]]:
  """Compile a small learning plan and explanation log.

  Steps:
  - prune highly mastered concepts
  - boost those with high impact & low mastery
  - respect dependencies by downranking items whose prereqs are weak
  """

  log: List[str] = []

  # 1) prune mastered
  candidates = []
  for cid, concept in concepts.items():
    b = beliefs.get(cid, Belief(0.5, 0.3, 0.2))
    score = _mastery_score(b)
    if score > 0.8:
      log.append(f"Pruned {concept.name} (mastery score {score:.2f})")
      continue
    candidates.append((cid, concept, b, score))

  priorities: List[Tuple[float, Concept, Belief]] = []

  for cid, concept, belief, score in candidates:
    nb = belief.normalised()
    uncertainty = 1.0 - score  # higher = more uncertain
    base = uncertainty * (1.0 + 0.3 * (concept.impact - 2))

    # Penalty if a prerequisite looks very weak
    weak_prereq = False
    for pre in concept.prerequisites:
      pb = beliefs.get(pre)
      if pb is None:
        continue
      if _mastery_score(pb) < 0.4:
        weak_prereq = True
        break
    if weak_prereq:
      base *= 0.7

    priorities.append((base, concept, belief))

  priorities.sort(key=lambda x: x[0], reverse=True)

  plan: List[PlanItem] = []
  for prio, concept, belief in priorities[:top_k]:
    action = choose_action(concept, belief)
    reason = build_reason(concept, belief, prio)
    plan.append(
      PlanItem(
        concept_id=concept.id,
        concept_name=concept.name,
        action=action,
        reason=reason,
        priority=prio,
      )
    )

  return plan, log


def choose_action(concept: Concept, belief: Belief) -> Literal["Read", "Simulate", "Example"]:
  b = belief.normalised()
  if b.unknown >= 0.55:
    return "Read"
  if b.mastered >= 0.45:
    return "Example"
  # In-between
  return "Simulate"


def build_reason(concept: Concept, belief: Belief, priority: float) -> str:
  b = belief.normalised()
  mastery = _mastery_score(b)
  return (
    f"Selected because mastery score is {mastery:.2f} (Unknown={b.unknown:.2f}, "
    f"Partial={b.partial:.2f}, Mastered={b.mastered:.2f}) and impact is {concept.impact}. "
    f"Priority score {priority:.2f}."
  )


def simulate_content(concept: Concept) -> str:
  """Return a tiny content snippet or simulation instructions.

  This is intentionally short – the UI will simply display this
  text when the concept is selected in the plan.
  """

  if concept.id == "emergency_fund":
    return (
      "If your monthly expenses are ₹20,000, a 3–6 month emergency fund "
      "means targeting ₹60,000–₹120,000. Compare this with your current savings."
    )
  if concept.id == "budgeting":
    return (
      "Use a 50-30-20 rule on an income of ₹40,000: ₹20,000 needs, "
      "₹12,000 wants, ₹8,000 savings. Try mapping your own numbers."
    )
  if concept.id == "mutual_funds_intro":
    return (
      "Simulate a SIP of ₹2,000/month at 10% annual return for 10 years – "
      "you end up near ₹4 lakh. Notice how time and consistency matter."
    )
  return concept.description
