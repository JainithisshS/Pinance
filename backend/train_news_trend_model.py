"""CLI helper to train the Agent B news trend model.

Run this once from the backend directory to generate
``news_trend_model.joblib`` used by /analyze_news.

Example::

    python -m train_news_trend_model

This will print a small validation report on synthetic data and then
save the model.
"""

from .news_model import train_and_save_trend_model


def main() -> None:
    print("[Agent B] Starting news trend model training...")
    train_and_save_trend_model()
    print("[Agent B] News trend model saved to news_trend_model.joblib")


if __name__ == "__main__":  # pragma: no cover - manual entry point
    main()
