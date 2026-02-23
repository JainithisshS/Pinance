from __future__ import annotations

"""Risk model utilities for Agent A.

Trains and serves a Random Forest classifier on synthetic monthly
finance profiles. The model is intentionally lightweight and
fully self-contained for a student project.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Optional, Tuple

import joblib
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split

BASE_DIR = Path(__file__).parent
MODEL_PATH = BASE_DIR / "risk_model.joblib"

RiskLabel = Literal["low", "medium", "high"]


@dataclass
class MonthlyFeatures:
    income: float
    fixed_expenses: float
    variable_expenses: float
    savings: float
    savings_rate: float  # savings / income, clipped to [‑1, 1]
    variable_share: float  # variable_expenses / max(income, 1)

    def as_vector(self) -> np.ndarray:
        return np.array(
            [
                self.income,
                self.fixed_expenses,
                self.variable_expenses,
                self.savings,
                self.savings_rate,
                self.variable_share,
            ],
            dtype=float,
        )


def _build_synthetic_dataset(n: int = 900) -> Tuple[np.ndarray, np.ndarray]:
    """Generate a synthetic dataset of monthly profiles with risk labels.

    This is heuristic and only for demonstration – the goal is to
    capture plausible patterns for low / medium / high risk.
    """

    rng = np.random.default_rng(42)
    X: list[np.ndarray] = []
    y: list[int] = []

    def label_to_int(lbl: RiskLabel) -> int:
        return {"low": 0, "medium": 1, "high": 2}[lbl]

    # Low risk months: good savings rate, moderate variable share
    for _ in range(n // 3):
        income = float(rng.uniform(15000, 80000))
        fixed = float(rng.uniform(0.2, 0.4) * income)
        variable = float(rng.uniform(0.1, 0.3) * income)
        total_exp = fixed + variable
        savings = max(income - total_exp, 0.0)
        savings_rate = float(np.clip(savings / max(income, 1.0), 0.0, 1.0))
        variable_share = float(np.clip(variable / max(income, 1.0), 0.0, 1.0))
        feat = MonthlyFeatures(
            income=income,
            fixed_expenses=fixed,
            variable_expenses=variable,
            savings=savings,
            savings_rate=savings_rate,
            variable_share=variable_share,
        )
        X.append(feat.as_vector())
        y.append(label_to_int("low"))

    # Medium risk months: okay savings, higher variable share
    for _ in range(n // 3):
        income = float(rng.uniform(15000, 80000))
        fixed = float(rng.uniform(0.25, 0.5) * income)
        variable = float(rng.uniform(0.2, 0.5) * income)
        total_exp = fixed + variable
        savings = income - total_exp
        savings_rate = float(np.clip(savings / max(income, 1.0), -0.2, 0.3))
        variable_share = float(np.clip(variable / max(income, 1.0), 0.1, 0.8))
        feat = MonthlyFeatures(
            income=income,
            fixed_expenses=fixed,
            variable_expenses=variable,
            savings=savings,
            savings_rate=savings_rate,
            variable_share=variable_share,
        )
        X.append(feat.as_vector())
        y.append(label_to_int("medium"))

    # High risk months: poor / negative savings, very high variable share
    for _ in range(n // 3):
        income = float(rng.uniform(15000, 80000))
        fixed = float(rng.uniform(0.3, 0.6) * income)
        variable = float(rng.uniform(0.4, 1.2) * income)
        total_exp = fixed + variable
        savings = income - total_exp
        savings_rate = float(np.clip(savings / max(income, 1.0), -1.0, 0.1))
        variable_share = float(np.clip(variable / max(income, 1.0), 0.3, 1.2))
        feat = MonthlyFeatures(
            income=income,
            fixed_expenses=fixed,
            variable_expenses=variable,
            savings=savings,
            savings_rate=savings_rate,
            variable_share=variable_share,
        )
        X.append(feat.as_vector())
        y.append(label_to_int("high"))

    return np.vstack(X), np.array(y, dtype=int)


def train_and_save_model(path: Path = MODEL_PATH) -> None:
    """Train a Random Forest risk classifier and report validation metrics.

    This function generates a synthetic dataset, performs a
    train/validation split, prints a classification report,
    then trains on the full dataset and saves the model.
    """

    X, y = _build_synthetic_dataset()

    # Basic validation on a hold‑out split
    X_train, X_val, y_train, y_val = train_test_split(
        X,
        y,
        test_size=0.25,
        random_state=42,
        stratify=y,
    )

    clf = RandomForestClassifier(
        n_estimators=120,
        max_depth=6,
        random_state=42,
        class_weight="balanced_subsample",
    )
    clf.fit(X_train, y_train)

    y_pred = clf.predict(X_val)
    print("Risk model validation report (synthetic data):")
    print(classification_report(y_val, y_pred, target_names=["low", "medium", "high"]))

    # Retrain on all data before saving for production use
    clf.fit(X, y)
    path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(clf, path)


_model: Optional[RandomForestClassifier] = None


def _load_model() -> Optional[RandomForestClassifier]:
    global _model
    if _model is None:
        if MODEL_PATH.exists():
            _model = joblib.load(MODEL_PATH)
    return _model


def _int_to_label(idx: int) -> RiskLabel:
    return {0: "low", 1: "medium", 2: "high"}.get(idx, "medium")


def predict_risk(features: MonthlyFeatures) -> Optional[tuple[RiskLabel, float]]:
    """Predict ML risk label and confidence for a monthly profile.

    Returns (label, probability) or None if the model is not available.
    """

    model = _load_model()
    if model is None:
        return None

    X = features.as_vector().reshape(1, -1)
    probs = model.predict_proba(X)[0]
    idx = int(np.argmax(probs))
    label = _int_to_label(idx)
    confidence = float(probs[idx])
    return label, confidence


def explain_risk(label: RiskLabel, features: MonthlyFeatures) -> str:
    """Simple rule-based explanation string for the ML risk label."""

    sr = features.savings_rate
    vs = features.variable_share
    sr_pct = sr * 100.0
    vs_pct = vs * 100.0

    if label == "low":
        return (
            f"Healthy month: you saved about {sr_pct:.0f}% of your income "
            f"and only ~{vs_pct:.0f}% went to flexible spends. "
            "You’re generally on track; just keep an eye on one or two big categories."
        )

    if label == "medium":
        msg = "Mixed signals: "
        if sr < 0.1:
            msg += f"savings are on the lower side at about {sr_pct:.0f}% of income, "
        else:
            msg += f"savings are okay at roughly {sr_pct:.0f}% of income, "
        if vs > 0.4:
            msg += f"and a big chunk (~{vs_pct:.0f}%) of income goes to flexible expenses. "
        else:
            msg += f"and variable spends are moderate (~{vs_pct:.0f}% of income). "
        msg += "Try nudging 5–10% of variable spends into savings."
        return msg

    # high
    msg = "High‑risk pattern: "
    if sr < 0:
        msg += f"you’re spending more than you earn this month (savings around {sr_pct:.0f}% of income), "
    else:
        msg += f"savings are very thin at roughly {sr_pct:.0f}% of income, "
    if vs > 0.6:
        msg += f"and a lot of money (~{vs_pct:.0f}% of income) is going into non‑fixed categories. "
    else:
        msg += f"with variable expenses still high (~{vs_pct:.0f}% of income). "
    msg += "Consider pausing one or two non‑essential expenses for a month."
    return msg
