# Project Roadmap – Agentic Finance System

This document is derived from the roadmap you shared, organized by phases.

## Phase 0 – Foundation
- Freeze scope, architecture, modules
- Set up Git repo and folder structure
- Backend: FastAPI, Frontend: Android (Kotlin)
- RAG DB: FAISS or Pinecone; LLM: OpenAI / Ollama
- Models: FinBERT, risk model, trend model

Deliverables:
- Architecture diagram (to be added in `architecture.md`)
- Tech stack document (`tech_stack.md`)
- Proposal + problem statement (`problem_statement.md`)
- Initial README

## Phase 1 – Core Data Ingestion
- Android notification listener to read bank/UPI SMS
- Parse messages into JSON: amount, merchant, category, date
- Backend expense categorization pipeline with regex + LLM fallback
- Store structured transactions and build monthly profiles

API:
- `POST /parse_message` – parse and store a single SMS/notification

## Phase 2 – Personal Finance Agent (Agent A)
- Compute EMI ratio, savings %, fixed/variable split
- Train risk score model (RandomForest/XGBoost)
- Generate suggestions based on risk profile
- Provide visual breakdowns (risk meter, categories)

API:
- `POST /analyze_finance`

## Phase 3 – News + Market Agent (Agent B)
- Use FinBERT for sentiment
- Entity and sector extraction
- Trend prediction model (XGBoost/LSTM) with UP/DOWN/NEUTRAL

API:
- `POST /analyze_news`

## Phase 4 – RAG System
- Collect finance documents and descriptions
- Chunk and embed with SentenceTransformers
- Store in FAISS/Pinecone and build retriever

API helper:
- `rag.retrieve()` (Python function + internal route later)

## Phase 5 – Decision Agent (Agent C)
- Combine outputs from Agent A + B + RAG
- Draft → Critique → Refine → Final loop
- Enforce evidence from retrieved docs

API:
- `POST /synthesize`

## Phase 6 – Integration
- Orchestrator wiring all agents
- Caching, error handling, prompt optimization

## Phase 7 – Android UI
- Chat-style UI, risk meter, graphs, news input, final insight card

## Phase 8 – MLOps & Model Management
- MLflow logging, model versioning, Docker, CI/CD

## Phase 9 – Evaluation & Research
- Baselines vs multi-agent + RAG
- Metrics: sentiment accuracy, trend AUC, risk precision, human scores

## Phase 10 – Finalization & Deployment
- Bug fixes, privacy and permissions
- APK, final report, slides, demo video, optional paper
