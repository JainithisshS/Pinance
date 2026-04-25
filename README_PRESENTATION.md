# 📊 Agentic Finance System — Presentation (PPT) Outline

---

## Slide 1: Title Slide

- **Project Title:** Agentic Finance System — AI-Powered Personal Finance & Market Insight Platform
- **Course:** Software Engineering (SEM-6)
- **Tech Stack (Tagline):** FastAPI · FinBERT NLP · Random Forest ML · Groq LLM · RAG · Bayesian Learning · Android (Kotlin)
- **Team / Author Names**

---

## Slide 2: Problem Statement

- Most retail users **struggle** to:
  - Track actual spending from bank SMS / UPI notifications
  - Understand personal financial risk (EMI burden, savings rate, fixed vs variable expenses)
  - Interpret daily financial news in the context of their own profile
- **Existing apps** are siloed:
  - Expense trackers ≠ Market analyzers ≠ Financial advisors
- **Gap:** No single system combines *personal transaction data* + *news sentiment* + *domain knowledge* into **explainable, agentic recommendations**

---

## Slide 3: Proposed Solution — Overview

- **End-to-end Agentic Finance Platform** with 3 autonomous AI agents:
  - **Agent A** — Personal Finance Analyzer (ML risk scoring + LLM insight)
  - **Agent B** — News & Market Sentiment Analyzer (FinBERT NLP + spaCy NER + trend forecasting)
  - **Agent C** — Decision Synthesizer Chatbot (RAG + Groq LLM multi-turn conversations)
- **Adaptive Learning Module** — Bayesian knowledge tracing + AI-generated micro-learning cards
- **Android App** — Notification listener captures bank SMS → auto-parses transactions
- **Cloud Deployment** — Docker + Render + Supabase (PostgreSQL) + Firebase Auth

---

## Slide 4: System Architecture (High-Level Diagram)

```
┌──────────────┐    HTTP/REST     ┌────────────────────────────────────┐
│  Android App │ ◄──────────────► │       FastAPI Backend (Python)     │
│  (Kotlin +   │                  │                                    │
│   Compose)   │                  │  ┌─────────┐  ┌─────────┐          │
│              │                  │  │ Agent A  │  │ Agent B  │        │
│  • SMS Parse │                  │  │ Finance  │  │ News NLP │        │
│  • Dashboard │                  │  │ Risk ML  │  │ FinBERT  │        │
│  • Chat UI   │                  │  └────┬─────┘  └────┬─────┘       │
│  • Learn     │                  │       └──────┬──────┘             │
│  • Market    │                  │         ┌────▼─────┐              │
└──────────────┘                  │         │ Agent C  │              │
                                  │         │ RAG+LLM  │              │
                                  │         │ Chatbot  │              │
                                  │         └──────────┘              │
                                  │                                   │
                                  │  ┌──────────┐  ┌──────────────┐   │
                                  │  │ Supabase │  │ SQLite (dev) │   │
                                  │  │ (Prod DB)│  │ (local DB)   │   │
                                  │  └──────────┘  └──────────────┘   │
                                  │                                   │
                                  │  ┌──────────────────────────────┐ │
                                  │  │ Adaptive Learning Engine     │ │
                                  │  │ Bayesian Belief + Groq AI   │ │                    
                                  │  └──────────────────────────────┘ │
                                  └────────────────────────────────────┘
```

---

## Slide 5: Tech Stack — Full Breakdown

| Layer            | Technology                                    | Purpose                                      |
|------------------|-----------------------------------------------|----------------------------------------------|
| **Backend**      | FastAPI + Uvicorn (Python 3.11)               | REST API framework, async request handling   |
| **Database**     | SQLite (dev) / Supabase PostgreSQL (prod)     | Dual-DB with automatic fallback              |
| **Auth**         | Firebase Admin SDK                            | JWT token verification, user management      |
| **ML — Risk**    | scikit-learn (Random Forest, 120 trees)       | Financial risk classification (low/med/high) |
| **ML — Category**| scikit-learn (TF-IDF + Logistic Regression)   | SMS transaction categorization (10 classes)  |
| **NLP — Sentiment** | HuggingFace FinBERT (ProsusAI/finbert)     | Finance-specific sentiment analysis          |
| **NLP — NER**    | spaCy (`en_core_web_sm`)                      | Named Entity Recognition for companies       |
| **NLP — Trend**  | Random Forest on FinBERT aggregate features   | Market trend prediction (bullish/bearish)    |
| **LLM**          | Groq Cloud (LLaMA-3.3-70B-Versatile)         | Agentic reasoning, chatbot, card generation  |
| **RAG Engine**   | Custom TF-IDF retrieval over 32-chunk corpus  | Knowledge-grounded financial recommendations |
| **Frontend**     | Android (Kotlin + Jetpack Compose)            | Mobile UI, notification listener             |
| **Deployment**   | Docker + Render.com                           | Cloud containerized deployment               |

---

## Slide 6: Module 1 — SMS Transaction Parsing

- **Input:** Raw bank SMS / UPI notification text
- **Pipeline:**
  1. **Regex Extraction** — Multi-pattern amount parser (Rs/INR/₹/rupees/debited/credited)
  2. **Merchant Detection** — Pattern: `"at <merchant_name>"`
  3. **Spam Filtering** — Rejects OTPs, promos, non-financial messages
  4. **Rule-Based Category** — Keyword maps for 10 categories (Food, Transport, Shopping, Bills, Groceries, Entertainment, Health, Education, Investment, Other)
  5. **ML Refinement** — TF-IDF + Logistic Regression with confidence threshold fallback
  6. **Credit/Debit Detection** — 15 credit keywords + 12 debit keywords for income vs expense
- **Storage:** SQLite / Supabase with user isolation
- **API:** `POST /api/parse_message`, `GET /api/transactions`, `PUT /api/transactions/{id}/category`, `DELETE /api/transactions`

---

## Slide 7: Module 2 — Agent A: Personal Finance Analyzer

- **Input:** Date range + user transactions from DB
- **Tier 1 (Traditional ML):**
  - Splits transactions into **income vs expenses** using credit/debit heuristics
  - Computes: total_income, total_expenses, savings, savings_rate, category breakdown
  - **Fixed vs Variable expense** classification by category names
  - **Month proration** — projects partial-month data to full-month estimates to avoid early-month bias
  - **Random Forest Risk Classifier** (6 features: income, fixed_exp, variable_exp, savings, savings_rate, variable_share)
  - Trained on 900 synthetic monthly profiles → 3-class: low/medium/high risk
  - Confidence bands (high ≥75%, medium ≥50%, low <50%) with early-month downgrade logic
  - **Human-readable risk explanations** based on savings rate and variable share
- **Tier 2 (Agentic LLM):**
  - Sends structured financial summary to Groq LLaMA-3.3-70B
  - System prompt: "You are Agent A, a personal finance analyst AI for an Indian user"
  - Generates **4–5 sentence personalized, data-specific insight** (not generic advice)
- **Logging:** Risk evaluations persisted to `risk_logs` table for offline model tuning
- **API:** `POST /api/analyze_finance`, `GET /api/risk_logs_summary`, `POST /api/expense_breakdown`

---

## Slide 8: Module 3 — Agent B: News & Market Analyzer

- **Data Sources:**
  - **Live RSS Feed** — Economic Times Market RSS (real-time Indian business news)
  - **Fallback** — 10 curated demo articles across Reliance, HDFC, TCS, Infosys, ICICI, etc.
  - **Live NSE Data** — Real-time index data pre-cached at server startup
- **NLP Pipeline:**
  - **FinBERT Sentiment** — ProsusAI/finbert transformer model; headline (40%) + body (60%) weighted
  - **spaCy NER** — Organization entity extraction for company identification
  - **Keyword Sentiment Fallback** — 27 positive keywords + 31 negative keywords + strong phrase detection
  - **Sector Mapping** — 25+ company-to-sector mappings (Banking, IT, Auto, Telecom, FMCG, Metals)
  - **Percentage & Points Move Extraction** — Regex patterns for "up 3%", "fell 150 pts"
  - **FinBERT Aggregate → Random Forest Trend Model** — bullish/sideways/bearish prediction
  - **Boilerplate Removal** — 13 regex patterns to clean editorial noise from articles
- **Tier 2 (Agentic LLM):**
  - Generates market narrative with sector-specific analysis via Groq LLM
  - Deep per-article analysis with confidence scoring and impact assessment
- **API:** `POST /api/analyze_news`, `GET /api/news_feed`, `POST /api/analyze_article`, `GET /api/live_market`

---

## Slide 9: Module 4 — Agent C: Decision Synthesizer & Chatbot

- **Input:** Agent A finance insight + Agent B news insight + user question + chat history
- **Tier 1 (RAG Retrieval):**
  - **Corpus:** 32 curated knowledge chunks covering emergency funds, budgeting (50/30/20), debt management (avalanche/snowball), SIP investing, diversification, tax saving (80C/80D), insurance, UPI safety, market crashes, retirement planning, scam detection
  - **Retrieval:** Custom TF-IDF scoring with IDF smoothing + tag bonus (×3 weight)
  - Top-k = 3 most relevant chunks
- **Tier 2 (Agentic LLM Chatbot):**
  - Multi-turn conversation with full history via Groq LLaMA-3.3-70B
  - Context injection: Agent A data + Agent B data + RAG knowledge
  - System prompt enforces: specific answers, INR amounts, no generic advice, conversation-aware
- **Deterministic Fallback:** Keyword-matching response generator covering 6 financial topics (budget, invest, debt, emergency, risk, what-if scenarios)
- **API:** `POST /api/synthesize`

---

## Slide 10: Module 5 — Adaptive Micro-Learning System

- **Knowledge Graph (DAG):**
  - 10 financial concepts across 4 difficulty levels
  - Prerequisites enforced (e.g., budgeting_basics requires income_basics + expense_tracking)
  - Acyclic graph validation with DFS cycle detection
- **Bayesian Knowledge Tracing:**
  - 3-state belief model: P(Unknown), P(Partial), P(Mastered) → always sum to 1.0
  - Bayesian update on quiz answers:
    - Fast correct (<30s): mastered +0.3
    - Slow correct (>30s): mastered +0.15, partial +0.15
    - Slow incorrect (>60s): unknown +0.4 (struggling)
    - Fast incorrect (<60s): unknown +0.2, partial +0.1 (guessing)
- **Curriculum Compiler:**
  - Scoring: `score = readiness × urgency × relevance`
  - Readiness = min mastery of prerequisites (threshold: 0.6)
  - Urgency = 1 − P(Mastered)
  - Relevance = context-aware multiplier (high risk → budget priority, high debt → debt priority)
  - Greedy selection of highest-scoring concept
- **AI Card Generation:**
  - Groq LLaMA generates content (150–200 words) + 4-option quiz per concept
  - Cards cached and pre-generated in background for instant delivery
  - Mastery level changes invalidate card cache for level-appropriate content
- **API:** `GET /api/learning/next-card`, `POST /api/learning/submit-answer`, `GET /api/learning/progress`, `GET /api/learning/explanation`

---

## Slide 11: Module 6 — Gamified Learning Roadmap

- **5 structured lessons:** Financial Basics → Budgeting → Emergency Fund → Debt Management → Investment Basics
- **Gamification:**
  - Points system: base 50 + perfect bonus 100 + speed bonus 20
  - Star ratings: 3★ (≥90%), 2★ (≥70%), 1★ (≥50%)
  - 6 achievements: First Steps 🔥, Bookworm 📚, Perfect Score 🎯, Speed Learner ⚡, Week Warrior 🏆, Master 💎
  - Daily goals tracking (lessons/questions/minutes)
  - Prerequisite-based lesson unlocking
- **API:** `GET /api/learning/roadmap`, `POST /api/learning/complete-lesson`, `GET /api/learning/stats`

---

## Slide 12: Android Mobile Application

- **Language:** Kotlin + Jetpack Compose
- **Key Screens:**
  - **Login** — Firebase Authentication
  - **Home/Dashboard** — Transaction history, expense breakdown
  - **Chat** — Agent C multi-turn financial chatbot
  - **Learn** — Reels-style swipeable micro-learning cards
  - **Learning Roadmap** — Gamified lesson progression
  - **Market** — Live market data + news analysis
- **Notification Listener Service:**
  - Captures bank/UPI notifications in real-time
  - Extracts title + text → sends to `/api/parse_message` automatically
  - Permission: `BIND_NOTIFICATION_LISTENER_SERVICE`
- **Networking:** Retrofit + Coroutines (async), Gson serialization
- **30 Kotlin source files** implementing full client-side logic

---

## Slide 13: Database & Persistence Architecture

- **Dual Database Strategy:**
  - **Development:** SQLite (`transactions.db`) — zero setup, local file
  - **Production:** Supabase (PostgreSQL) — cloud-hosted, automatic fallback
- **Tables:**
  - `transactions` (id, user_id, amount, merchant, category, currency, timestamp, raw_message)
  - `risk_logs` (id, user_id, dates, income, expenses, savings, heuristic_risk, ml_risk_level/confidence)
  - `belief_states` (id, user_id, concept_id, belief_unknown/partial/mastered, interaction_count)
  - `interaction_events` (id, user_id, card_id, concept_id, answer_index, is_correct, time_spent)
- **Indexes:** User-specific indexes on transactions and risk_logs
- **Auth:** Firebase JWT verification with development fallback

---

## Slide 14: ML/AI Models Summary

| Model                | Algorithm                     | Training Data    | Features           | Output                          |
|----------------------|-------------------------------|------------------|--------------------|---------------------------------|
| **Category Model**   | TF-IDF + Logistic Regression  | Synthetic SMS    | Raw text (TF-IDF)  | 10 spending categories          |
| **Risk Model**       | Random Forest (120 trees, depth 6) | 900 synthetic profiles | 6 financial ratios | low / medium / high risk       |
| **Trend Model**      | Random Forest (120 trees, depth 5) | 600 synthetic regimes  | 4 sentiment ratios | bullish / sideways / bearish   |
| **FinBERT**          | BERT transformer (fine-tuned) | Financial corpus  | Token embeddings   | positive / negative / neutral   |
| **spaCy NER**        | CNN + Transition-based parser | OntoNotes        | Token features     | ORG / PERSON / LOC entities    |
| **Groq LLaMA 3.3**  | 70B parameter LLM             | Web-scale        | Prompt + context   | Natural language generation     |

---

## Slide 15: Deployment & DevOps

- **Docker:** Multi-stage Dockerfile (Python 3.11-slim + PyTorch CPU + spaCy + transformers)
- **Render.com:** `render.yaml` with environment variables for Supabase, Groq, Firebase
- **HuggingFace Spaces:** Alternative deployment option (port 7860)
- **Environment Variables:** SUPABASE_URL, SUPABASE_SERVICE_KEY, GROQ_API_KEY, FIREBASE_ADMIN_SDK_JSON
- **Backend startup:** FinBERT preloaded in background thread + NSE cache pre-warmed

---

## Slide 16: Key Design Patterns & SE Principles

- **Multi-Agent Architecture:** 3 autonomous agents (A/B/C) with clear separation of concerns
- **Two-Tier Pipeline:** Traditional ML (Tier 1) + Agentic LLM (Tier 2) with graceful fallback
- **Singleton Pattern:** GroqClient, ConceptGraph, FinBERT pipeline, Supabase client
- **Strategy Pattern:** Dual database (SQLite ↔ Supabase) with automatic failover
- **Observer Pattern:** Android NotificationListenerService for real-time data capture
- **DAG Pattern:** Concept prerequisite graph with cycle detection (DFS)
- **Bayesian Inference:** Probabilistic knowledge state updates
- **RAG Pattern:** Retrieval-Augmented Generation with TF-IDF scoring
- **CORS Middleware, Auth Guards, Error Boundary, Background Tasks, Caching**

---

## Slide 17: API Endpoint Summary

| Endpoint                          | Method | Agent   | Description                              |
|-----------------------------------|--------|---------|------------------------------------------|
| `/api/parse_message`              | POST   | —       | Parse SMS into structured transaction    |
| `/api/transactions`               | GET    | —       | List user's transaction history          |
| `/api/transactions/{id}/category` | PUT    | —       | Update transaction category              |
| `/api/analyze_finance`            | POST   | A       | Financial risk analysis + LLM insight    |
| `/api/expense_breakdown`          | POST   | A       | Category-wise spending breakdown         |
| `/api/risk_logs_summary`          | GET    | A       | Aggregated risk evaluation history       |
| `/api/analyze_news`               | POST   | B       | Topic-based news sentiment analysis      |
| `/api/news_feed`                  | GET    | B       | Curated financial news feed              |
| `/api/analyze_article`            | POST   | B       | Deep single-article analysis             |
| `/api/live_market`                | GET    | B       | Real-time NSE market indices             |
| `/api/synthesize`                 | POST   | C       | RAG + LLM decision chatbot               |
| `/api/learning/next-card`         | GET    | Learn   | Bayesian-selected micro-learning card    |
| `/api/learning/submit-answer`     | POST   | Learn   | Quiz submission + belief update          |
| `/api/learning/progress`          | GET    | Learn   | User's overall learning progress         |
| `/api/learning/roadmap`           | GET    | Learn   | Gamified learning roadmap                |
| `/api/learning/complete-lesson`   | POST   | Learn   | Mark lesson complete, award points       |
| `/api/learning/stats`             | GET    | Learn   | User learning statistics                 |

---

## Slide 18: Results & Demo Highlights

- ✅ Real-time SMS parsing with 10-category ML classification
- ✅ ML risk model with confidence bands and human-readable explanations
- ✅ FinBERT NLP on live Economic Times RSS news feed
- ✅ Multi-turn AI chatbot grounded in RAG finance knowledge base
- ✅ Adaptive learning with Bayesian knowledge tracing and AI-generated quiz cards
- ✅ Gamified learning roadmap with achievements and streaks
- ✅ Full Android app with notification capture, dashboard, chat, and learning screens
- ✅ Cloud-deployed with Docker + Supabase + Firebase Auth

---

## Slide 19: Challenges & Learnings

- Handling noisy SMS text with 3-tier amount parsing (regex + debit/credit fallback + last-resort number extraction)
- FinBERT label mapping verification (ProsusAI/finbert id2label: 0=positive, 1=negative, 2=neutral)
- Early-month bias in risk assessment → month proration logic for fair risk scoring
- Boilerplate removal from news articles (13 regex patterns) to avoid sentiment pollution
- Bayesian normalization ensuring probabilities always sum to 1.0
- LLM fallback design: every LLM-powered feature has a working deterministic fallback
- Dual-database strategy with seamless Supabase ↔ SQLite failover

---

## Slide 20: Future Scope

- Real FAISS/Pinecone vector store for semantic RAG retrieval
- Self-evaluation loop: Agent C critiques and refines its own recommendations
- Reinforcement learning from user feedback on recommendations
- Voice-based financial assistant using speech-to-text
- Portfolio tracking and investment integration (Zerodha/Groww API)
- Advanced time-series forecasting (LSTM/Prophet) for spending predictions
- Multi-language SMS parsing (Hindi, regional languages)

---

## Slide 21: Thank You / Q&A

- **Project:** Agentic Finance System
- **GitHub / Repository Link**
- **Live Demo URL** (Render deployment)
- **Questions?**
