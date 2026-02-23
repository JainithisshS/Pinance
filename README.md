# Agentic Finance System

End-to-end personal finance and market insight assistant.

This repository currently contains the **backend** implementation for your Software Engineering project, plus stubs for later agentic/RAG phases.

---

## Tech Stack

- **Backend:** FastAPI (Python)
- **Database:** SQLite (local file `transactions.db`)
- **ML:** scikit-learn (TF‑IDF + Logistic Regression for SMS category)
- **Tests:** pytest
- **Docs:** Markdown in `docs/`

Planned (future work, not fully implemented yet):

- **Frontend:** Android (Kotlin)
- **RAG DB:** FAISS or Pinecone
- **LLM:** OpenAI / Ollama

---

## Project Structure

Key folders and files:

- `backend/`
	- `main.py` – FastAPI app entrypoint.
	- `models.py` – Pydantic models for requests/responses.
	- `db.py` – SQLite connection + `transactions` table.
	- `routers/`
		- `transactions.py` – Phase 1: `/api/parse_message` and `/api/health`.
		- `finance_analysis.py` – Agent A stub: `/api/analyze_finance`.
		- `news_analysis.py` – Agent B stub: `/api/analyze_news`.
		- `recommendation.py` – Agent C stub: `/api/synthesize`.
	- `category_model.py` – Helper to load/use the trained category classifier.
	- `generate_synthetic_dataset.py` – Creates synthetic SMS-like dataset.
	- `train_category_model.py` – Trains scikit-learn category model.
	- `requirements.txt` – Python dependencies.
- `docs/`
	- `architecture.md` – High-level architecture summary.
	- `roadmap.md` – Phase-by-phase roadmap.
	- `problem_statement.md`, `tech_stack.md` – SE documentation.

---

## How to Run the Backend

From PowerShell on Windows:

```powershell
cd "c:\Users\jaini\OneDrive\Desktop\SEM-6\Software Engg\agentic-finance-system"
python -m pip install -r backend/requirements.txt

uvicorn backend.main:app --reload
```

The API will be available at: http://127.0.0.1:8000

---

## Core API Endpoints (Implemented)

Base URL: `http://127.0.0.1:8000`

- `GET /`  
	Health message for the backend.

- `GET /api/health`  
	Simple health check: returns `{ "status": "ok" }`.

- `POST /api/parse_message`  
	**Purpose:** Phase 1 – parse an SMS/notification into a structured transaction and store it in SQLite.

	Example request body:

	```json
	{
		"raw_message": "INR 500.00 spent at Swiggy on your card"
	}
	```

	Behavior:

	- Extracts `amount` and `merchant` using regex.
	- Infers a `category` using:
		- Rule-based keywords (Food & Dining, Transport, Shopping, etc.).
		- Then refines with the ML model (`category_model.joblib`) if available, with a confidence check.
	- Inserts the transaction into the `transactions` table.
	- Returns the saved transaction, including generated `id`.

---

## Agent Stubs (Future Phases)

These endpoints are **placeholders** to show the design for later phases:

- `POST /api/analyze_finance` (Agent A – personal finance analysis)  
	- Input: `start_date`, `end_date`.
	- Output: Reads real transactions from SQLite for that date range, computes:
		- `total_spent`
		- `transactions_count`
		- `top_category`
		- `risk_level` (low/medium/high based on total spending)
		- Human-readable `message` summarizing the period.

- `POST /api/analyze_news` (Agent B – news and market analysis)  
	- Input: `topic`.
	- Output: a simple keyword-based sentiment analysis over mock headlines:
		- `overall_sentiment` (positive/negative/neutral)
		- `sentiment_breakdown` (counts of positive/negative/neutral headlines)
		- `sample_articles` (few example titles for the topic)
		- `summary` (short human-readable explanation).

- `POST /api/synthesize` (Agent C – recommendation agent)  
	- Input: `finance_insight`, `news_insight`.
	- Output: combines both insights with a tiny RAG engine over a small finance knowledge base:
		- `recommendation` – simple consolidated suggestion.
		- `rationale` – explains how finance + news + knowledge were combined.
		- `retrieved_knowledge` – list of short tips retrieved from the in-memory corpus.

These stubs are ready to be replaced later with real LLM + RAG logic.

---

## Running Tests

From the project root:

```powershell
cd "c:\Users\jaini\OneDrive\Desktop\SEM-6\Software Engg\agentic-finance-system"
pytest backend/tests/test_api.py
```

Current tests cover:

- `GET /api/health` – checks status and JSON.
- `POST /api/parse_message` – checks amount parsing, category (Food & Dining), and that an `id` is returned.

---

## Data & Model Utilities

Optional steps if you want to regenerate the dataset and retrain the model:

```powershell
cd "c:\Users\jaini\OneDrive\Desktop\SEM-6\Software Engg\agentic-finance-system\backend"

# 1. Generate synthetic SMS-like dataset
python generate_synthetic_dataset.py

# 2. Train category model and save category_model.joblib
python train_category_model.py
```

The `/api/parse_message` endpoint will automatically use the new `category_model.joblib` if present.

---

## RAG Demo Module

The folder `backend/rag/` contains a minimal retrieval-only example:

- `rag_engine.py` – tiny in-memory corpus of finance best practices and a `retrieve(query, k)` function that scores chunks by keyword overlap.

The `/api/synthesize` endpoint uses this `retrieve` function to pull a few relevant tips and includes them in the API response as `retrieved_knowledge`. This demonstrates how RAG would conceptually plug into the agent pipeline without needing external APIs or API keys.

---

## Roadmap

High-level phases (see docs for details):

1. Core data ingestion (SMS/notification parsing) – **implemented**.
2. Personal finance analyzer (risk scoring) – **stubbed**.
3. News + market agent – **stubbed**.
4. RAG knowledge engine – **planned**.
5. Decision agent + self-eval loop – **planned**.
6. Integration & orchestration – **planned**.
7. Android UI – **planned**.
8. MLOps & deployment – **planned**.

See `docs/roadmap.md` for the detailed phase-by-phase plan.
