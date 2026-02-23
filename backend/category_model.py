from pathlib import Path

import joblib


BASE_DIR = Path(__file__).parent
MODEL_PATH = BASE_DIR / "category_model.joblib"

_category_model = None


def _load_model():
    global _category_model
    if _category_model is None and MODEL_PATH.exists():
        _category_model = joblib.load(MODEL_PATH)
    return _category_model


def predict_category(text: str, default: str = "Other", min_confidence: float = 0.5) -> str:
    """Predict a category using the trained model, if available.

    Uses prediction probability and falls back to ``default`` when
    confidence is below ``min_confidence`` or on any error.
    """

    model = _load_model()
    if model is None:
        return default

    try:
        # Prefer probability-based prediction if available
        if hasattr(model, "predict_proba"):
            proba = model.predict_proba([text])[0]
            best_idx = int(proba.argmax())
            best_conf = float(proba[best_idx])
            if best_conf < min_confidence:
                return default
            # ``classes_`` is exposed on the pipeline
            label = model.classes_[best_idx]
            return str(label)

        # Fallback: plain predict without confidence
        pred = model.predict([text])[0]
        return str(pred)
    except Exception:
        return default
