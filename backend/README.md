# Backend

FastAPI backend for the Agentic Finance System.

It handles transaction parsing, financial analysis, market/news intelligence, recommendation synthesis, and adaptive learning. The service uses Firebase Auth, supports SQLite for local development and Supabase for production, and integrates ML models plus Groq-backed LLM fallbacks where available.

## Main Areas

- `transactions`: SMS and notification parsing, transaction CRUD, category overrides.
- `finance_analysis`: spending summaries, risk scoring, and risk logs.
- `news_analysis`: FinBERT sentiment, article analysis, and live market context.
- `recommendation`: RAG-grounded synthesis and chatbot responses.
- `learning`: micro-learning, roadmap, and progress tracking.
- `adaptive_learning`: concept graph and curriculum logic.

## Local Run

From the backend directory:

```bash
uvicorn main:app --reload
```

## Notes

- Read the project overview in [../README.md](../README.md).
- The backend README is intentionally short; the root README is the primary entry point for the full repository.
