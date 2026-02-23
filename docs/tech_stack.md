# Tech Stack – Agentic Finance System

## Backend
- **Language:** Python 3.10+
- **Framework:** FastAPI
- **Server:** Uvicorn
- **Database:** SQLite (initially) or Postgres (later)
- **Vector Store:** FAISS (local) or Pinecone (managed)

## Frontend
- **Platform:** Android
- **Language:** Kotlin
- **UI:** Jetpack Compose or XML-based UI

## ML / NLP
- **Risk Model:** RandomForest/XGBoost (scikit-learn/xgboost)
- **News Sentiment:** FinBERT (Hugging Face transformers)
- **Trend Model:** XGBoost/LSTM (depending on time)
- **Embeddings:** SentenceTransformers (e.g., `all-MiniLM-L6-v2`)

## LLM & RAG
- **LLM Provider:** OpenAI or local Ollama
- **RAG:** SentenceTransformers + FAISS/Pinecone
- **Agent Orchestration:** Python modules coordinating Agent A/B/C

## DevOps / MLOps (Later Phases)
- MLflow for experiment tracking and model registry
- Docker for backend containerization
- GitHub Actions for CI/CD
