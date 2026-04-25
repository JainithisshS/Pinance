# Resume Entry

## PROJECTS

**Agentic Finance System: AI-Powered Personal Finance & Market Intelligence Platform**  
Dec 2025 – Mar 2026  
*Python, FastAPI, Kotlin, Machine Learning, NLP, LLMs, Firebase, Supabase, Android*

• Built end-to-end multi-agent financial platform with 3 autonomous agents (Finance Analyzer, Market Intelligence, Decision Synthesizer) using Two-Tier Architecture (ML + LLM with deterministic fallbacks) ensuring 100% core feature availability.

• Developed SMS transaction parser with 3-tier regex extraction pipeline (currency pattern → keyword proximity → digit fallback) handling diverse Indian bank formats across 10 spending categories using TF-IDF + Logistic Regression classifier.

• Implemented Real-time market sentiment analysis using FinBERT (finance-domain NLP) with spaCy NER for sector-level entity extraction; Random Forest trend model predicts bullish/bearish/sideways market direction with 70+ domain keywords fallback.

• Designed RAG-powered chatbot with 32-chunk financial knowledge corpus, custom TF-IDF retrieval with tag-match bonus weighting, grounded in user's personal financial data (Agent A outputs) and market sentiment (Agent B outputs) via Groq LLaMA 3.3 70B.

• Created Bayesian Knowledge Tracing adaptive learning system with 3-state belief model (Unknown/Partial/Mastered), latency-aware updates, DAG-structured concept graph (11 concepts), profile-aware curriculum compilation; 5-lesson gamified roadmap with points, stars, and achievements.

• Engineered robust backend with 17 REST endpoints (FastAPI), dual-database strategy (Supabase PostgreSQL + SQLite), Firebase JWT authentication, graceful degradation across 3 fallback tiers (deterministic rules → ML models → cached responses).

• Developed Android client (Kotlin, Jetpack Compose) with NotificationListenerService for automatic SMS capture, backend-authoritative parsing design for rapid iteration, 30 source files across 6 screens (Login, Dashboard, Chat, Learn, Roadmap, Market).

• Trained 6 ML/AI models on 2,500+ synthetic profiles: Risk classification (Random Forest, 120 trees), Category prediction, Market trend forecasting, FinBERT sentiment, spaCy NER, LLaMA 3.3 70B LLM reasoning.

• Generated comprehensive 8-page IEEE technical report covering Problem Statement (5-point competitive analysis), Technology Stack (12-layer architecture), Features (7 subsections), and Novelty (6 distinct contributions).
