from __future__ import annotations

"""News sentiment and trend utilities for Agent B.

This module uses a **proper NLP pipeline** (HuggingFace FinBERT) for
finance-domain sentiment classification.  The pipeline tokenizes each
headline / article, runs it through the transformer, and reads the
softmax probabilities to produce *positive*, *negative*, or *neutral*
labels with confidence scores.

A small RandomForest model maps aggregate sentiment features to a
short-term trend label (bullish / bearish / sideways).
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Optional, Sequence, Tuple

import joblib
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split

# ---------------------------------------------------------------------------
# HuggingFace / FinBERT imports (optional but strongly preferred)
# ---------------------------------------------------------------------------
try:
    import logging as _logging
    _logging.getLogger("tensorflow").setLevel(_logging.ERROR)

    from transformers import (
        AutoModelForSequenceClassification,
        AutoTokenizer,
        pipeline as hf_pipeline,
    )
    import torch

    _HF_AVAILABLE = True
except Exception:  # pragma: no cover
    AutoModelForSequenceClassification = None  # type: ignore
    AutoTokenizer = None  # type: ignore
    hf_pipeline = None  # type: ignore
    torch = None  # type: ignore
    _HF_AVAILABLE = False


BASE_DIR = Path(__file__).parent
TREND_MODEL_PATH = BASE_DIR / "news_trend_model.joblib"

TrendLabel = Literal["bullish", "bearish", "sideways"]

# ---------------------------------------------------------------------------
# FinBERT model name and CORRECT label mapping (verified via AutoConfig)
# ProsusAI/finbert id2label: {0: "positive", 1: "negative", 2: "neutral"}
# ---------------------------------------------------------------------------
_FINBERT_MODEL_NAME = "ProsusAI/finbert"
_FINBERT_ID2LABEL = {0: "positive", 1: "negative", 2: "neutral"}

# NLP pipeline singleton
_sentiment_pipeline = None
_PIPELINE_LOADED = False


def _get_sentiment_pipeline():
    """Return a HuggingFace sentiment-analysis pipeline using FinBERT.

    The pipeline handles tokenization, inference, softmax, and label
    mapping internally — no manual label dict needed.

    Returns ``None`` if transformers/torch are unavailable.
    """
    global _sentiment_pipeline, _PIPELINE_LOADED

    if _PIPELINE_LOADED:
        return _sentiment_pipeline

    _PIPELINE_LOADED = True

    if not _HF_AVAILABLE or hf_pipeline is None:
        return None

    try:
        _sentiment_pipeline = hf_pipeline(
            "sentiment-analysis",
            model=_FINBERT_MODEL_NAME,
            tokenizer=_FINBERT_MODEL_NAME,
            top_k=None,        # return all 3 labels with probabilities
            truncation=True,
            max_length=512,
        )
        print("[Agent B] FinBERT NLP pipeline loaded successfully.")
    except Exception as exc:
        print(f"[Agent B] Warning: could not load FinBERT pipeline: {exc}")
        _sentiment_pipeline = None

    return _sentiment_pipeline


@dataclass
class NewsSentimentFeatures:
    """Aggregate sentiment features for a batch of headlines/articles."""

    positive_ratio: float
    negative_ratio: float
    neutral_ratio: float
    net_score: float  # positive_ratio - negative_ratio

    def as_vector(self) -> np.ndarray:
        return np.array(
            [
                self.positive_ratio,
                self.negative_ratio,
                self.neutral_ratio,
                self.net_score,
            ],
            dtype=float,
        )


def finbert_sentiment(texts: Sequence[str]) -> Optional[list[tuple[str, float]]]:
    """Run FinBERT NLP pipeline on a batch of texts.

    Returns a list of ``(label, confidence)`` tuples where *label* is
    ``"positive"``, ``"negative"``, or ``"neutral"`` and *confidence*
    is the softmax probability for that prediction.

    Uses the HuggingFace ``pipeline()`` API which handles tokenization,
    inference, softmax, and label mapping correctly from the model config.

    If transformers are not available, returns ``None`` so the caller
    can fall back to simpler methods.
    """
    if not texts:
        return []

    pipe = _get_sentiment_pipeline()
    if pipe is None:
        return None

    try:
        all_results = pipe(list(texts))
    except Exception as exc:
        print(f"[Agent B] FinBERT inference error: {exc}")
        return None

    output: list[tuple[str, float]] = []
    for result_set in all_results:
        # result_set is a list of dicts (one per label, sorted by score):
        # [{"label": "positive", "score": 0.93}, {"label": "neutral", ...}, ...]
        best = max(result_set, key=lambda x: x["score"])
        label = best["label"].lower()
        if label not in ("positive", "negative", "neutral"):
            label = "neutral"
        output.append((label, float(best["score"])))

    return output


def finbert_sentiment_detailed(texts: Sequence[str]) -> Optional[list[dict[str, float]]]:
    """Run FinBERT and return **all three probabilities** per text.

    Returns a list of dicts like::

        [{"positive": 0.85, "negative": 0.05, "neutral": 0.10}, ...]

    Useful when you need the full probability distribution, not just
    the argmax label.
    """
    if not texts:
        return []

    pipe = _get_sentiment_pipeline()
    if pipe is None:
        return None

    try:
        all_results = pipe(list(texts))
    except Exception as exc:
        print(f"[Agent B] FinBERT inference error: {exc}")
        return None

    output: list[dict[str, float]] = []
    for result_set in all_results:
        probs = {"positive": 0.0, "negative": 0.0, "neutral": 0.0}
        for item in result_set:
            lbl = item["label"].lower()
            if lbl in probs:
                probs[lbl] = float(item["score"])
        output.append(probs)

    return output


def _build_synthetic_trend_dataset(n: int = 600) -> tuple[np.ndarray, np.ndarray]:
    """Generate a synthetic dataset mapping sentiment to trend labels.

    This mirrors the style of :mod:`risk_model` – the dataset is
    heuristic but captures the intuitive idea that strongly positive
    sentiment skews bullish, strongly negative skews bearish and
    balanced sentiment is sideways.
    """

    rng = np.random.default_rng(123)
    X: list[np.ndarray] = []
    y: list[int] = []

    def label_to_int(lbl: TrendLabel) -> int:
        return {"bullish": 0, "sideways": 1, "bearish": 2}[lbl]

    # Bullish regimes: high positive ratio, low negative, positive net score
    for _ in range(n // 3):
        pos = float(rng.uniform(0.4, 0.9))
        neg = float(rng.uniform(0.0, 0.3))
        neu = max(0.0, 1.0 - pos - neg)
        net = pos - neg
        feat = NewsSentimentFeatures(pos, neg, neu, net)
        X.append(feat.as_vector())
        y.append(label_to_int("bullish"))

    # Bearish regimes: high negative ratio, low positive, negative net score
    for _ in range(n // 3):
        neg = float(rng.uniform(0.4, 0.9))
        pos = float(rng.uniform(0.0, 0.3))
        neu = max(0.0, 1.0 - pos - neg)
        net = pos - neg
        feat = NewsSentimentFeatures(pos, neg, neu, net)
        X.append(feat.as_vector())
        y.append(label_to_int("bearish"))

    # Sideways regimes: mixed or balanced sentiment
    for _ in range(n // 3):
        pos = float(rng.uniform(0.2, 0.5))
        neg = float(rng.uniform(0.2, 0.5))
        # Ensure the mix is not too skewed
        if abs(pos - neg) > 0.15:
            mid = (pos + neg) / 2
            pos = mid + rng.uniform(-0.05, 0.05)
            neg = mid - rng.uniform(-0.05, 0.05)
        neu = max(0.0, 1.0 - pos - neg)
        net = pos - neg
        feat = NewsSentimentFeatures(pos, neg, neu, net)
        X.append(feat.as_vector())
        y.append(label_to_int("sideways"))

    return np.vstack(X), np.array(y, dtype=int)


def train_and_save_trend_model(path: Path = TREND_MODEL_PATH) -> None:
    """Train a small RandomForest trend model and report validation metrics."""

    X, y = _build_synthetic_trend_dataset()

    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.25, random_state=42, stratify=y
    )

    clf = RandomForestClassifier(
        n_estimators=120,
        max_depth=5,
        random_state=42,
        class_weight="balanced_subsample",
    )
    clf.fit(X_train, y_train)

    y_pred = clf.predict(X_val)
    print("News trend model validation report (synthetic data):")
    print(classification_report(y_val, y_pred, target_names=["bullish", "sideways", "bearish"]))

    # Retrain on all data before saving for use in the API
    clf.fit(X, y)
    path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(clf, path)


_trend_model: Optional[RandomForestClassifier] = None


def _load_trend_model() -> Optional[RandomForestClassifier]:
    global _trend_model
    if _trend_model is None and TREND_MODEL_PATH.exists():
        _trend_model = joblib.load(TREND_MODEL_PATH)
    return _trend_model


def predict_trend(features: NewsSentimentFeatures) -> Optional[tuple[TrendLabel, float]]:
    """Predict a short-term trend label from aggregate sentiment features.

    Returns ``(label, confidence)`` or ``None`` if the trend model is
    not yet trained/saved.
    """

    model = _load_trend_model()
    if model is None:
        return None

    X = features.as_vector().reshape(1, -1)
    probs = model.predict_proba(X)[0]
    idx = int(np.argmax(probs))
    label = {0: "bullish", 1: "sideways", 2: "bearish"}.get(idx, "sideways")
    confidence = float(probs[idx])
    return label, confidence
