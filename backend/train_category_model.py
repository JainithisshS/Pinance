from pathlib import Path

import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline


BASE_DIR = Path(__file__).parent
DATA_PATH = BASE_DIR / "data" / "transactions_labeled.csv"
MODEL_PATH = BASE_DIR / "category_model.joblib"

CATEGORIES = [
    "Food & Dining",
    "Transport",
    "Shopping",
    "Bills & Utilities",
    "Groceries",
    "Entertainment",
    "Health",
    "Education",
    "Investment",
    "Other",
]


def load_data(path: Path) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"Dataset not found at {path}. Please create transactions_labeled.csv as documented in data/README.md.")
    df = pd.read_csv(path)
    if "text" not in df.columns or "category" not in df.columns:
        raise ValueError("CSV must contain 'text' and 'category' columns.")
    df = df.dropna(subset=["text", "category"])
    df["text"] = df["text"].astype(str)
    df["category"] = df["category"].astype(str)
    return df


def train_model(df: pd.DataFrame):
    X = df["text"]
    y = df["category"]

    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=0.2,
        stratify=y,
        random_state=42,
    )

    pipeline = Pipeline(
        steps=[
            (
                "tfidf",
                TfidfVectorizer(
                    ngram_range=(1, 2),
                    max_features=20000,
                    lowercase=True,
                ),
            ),
            (
                "clf",
                LogisticRegression(max_iter=200, n_jobs=-1),
            ),
        ]
    )

    pipeline.fit(X_train, y_train)

    y_pred = pipeline.predict(X_test)
    print(classification_report(y_test, y_pred, labels=CATEGORIES))

    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(pipeline, MODEL_PATH)
    print(f"Saved category model to {MODEL_PATH}")


def main() -> None:
    df = load_data(DATA_PATH)
    print(f"Loaded {len(df)} labelled messages from {DATA_PATH}")
    train_model(df)


if __name__ == "__main__":
    main()
