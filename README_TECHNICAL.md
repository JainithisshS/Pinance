# 🔧 Agentic Finance System — Full Technical Documentation

> **End-to-end AI-powered personal finance and market insight platform built with a Multi-Agent Architecture, NLP pipelines, ML classifiers, RAG retrieval, Bayesian knowledge tracing, and a Kotlin Android client.**

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Backend Implementation](#4-backend-implementation)
5. [Agent A — Personal Finance Analyzer](#5-agent-a--personal-finance-analyzer)
6. [Agent B — News & Market Sentiment Analyzer](#6-agent-b--news--market-sentiment-analyzer)
7. [Agent C — RAG Decision Synthesizer & Chatbot](#7-agent-c--rag-decision-synthesizer--chatbot)
8. [SMS Transaction Parsing Engine](#8-sms-transaction-parsing-engine)
9. [ML Models — Training & Inference](#9-ml-models--training--inference)
10. [NLP Pipeline — FinBERT & spaCy](#10-nlp-pipeline--finbert--spacy)
11. [RAG Knowledge Engine](#11-rag-knowledge-engine)
12. [Adaptive Micro-Learning System](#12-adaptive-micro-learning-system)
13. [Gamified Learning Roadmap](#13-gamified-learning-roadmap)
14. [Database Layer](#14-database-layer)
15. [Authentication System](#15-authentication-system)
16. [Android Mobile Application](#16-android-mobile-application)
17. [API Reference](#17-api-reference)
18. [Deployment](#18-deployment)
19. [Project Structure](#19-project-structure)
20. [Design Patterns & SE Principles](#20-design-patterns--se-principles)
21. [How to Run](#21-how-to-run)

---

## 1. Project Overview

The **Agentic Finance System** is a multi-agent AI platform that:

1. **Ingests** personal finance data by parsing Indian bank SMS / UPI notification text using regex + ML classification
2. **Analyzes** personal spending, income, savings, and risk using a Random Forest ML model + heuristic rules
3. **Understands** financial news sentiment using FinBERT NLP transformer + spaCy NER + Random Forest trend prediction
4. **Recommends** actions via a RAG-grounded LLM chatbot that combines personal finance context, news sentiment, and curated financial knowledge
5. **Teaches** financial literacy through Bayesian knowledge tracing with AI-generated micro-learning cards

### Key Innovation: Two-Tier Agentic Pipeline

Every agent follows a **Tier 1 (Traditional ML) → Tier 2 (LLM Agentic)** architecture:
- **Tier 1:** Fast, deterministic, always-available ML/rule-based inference
- **Tier 2:** LLM-powered agentic reasoning via Groq (LLaMA 3.3 70B), with automatic fallback to Tier 1 if the LLM is unavailable

---

## 2. System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ANDROID CLIENT (Kotlin)                     │
│  ┌────────────┐ ┌────────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ Login      │ │ Dashboard  │ │ Chat     │ │ Learning Reels   │  │
│  │ (Firebase) │ │ (Home)     │ │ (Agent C)│ │ (Bayesian Cards) │  │
│  └────────────┘ └────────────┘ └──────────┘ └──────────────────┘  │
│  ┌────────────────────────────┐ ┌────────────────────────────────┐ │
│  │ NotificationListenerService│ │ Learning Roadmap (Gamified)    │ │
│  │ (Auto-captures bank SMS)  │ │ (Achievements, Streaks, Stars) │ │
│  └────────────────────────────┘ └────────────────────────────────┘ │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTP/REST (Retrofit + Coroutines)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    FASTAPI BACKEND (Python 3.11)                    │
│                                                                     │
│  ┌──── ROUTERS ────────────────────────────────────────────────┐   │
│  │ transactions.py     → SMS parsing, CRUD, category update    │   │
│  │ finance_analysis.py → Agent A: risk ML + LLM insight        │   │
│  │ news_analysis.py    → Agent B: FinBERT NLP + trend + LLM    │   │
│  │ recommendation.py   → Agent C: RAG + LLM chatbot            │   │
│  │ adaptive_learning.py→ Bayesian learning + AI card generation │   │
│  │ roadmap.py          → Gamified lesson progression           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──── SERVICES ───────────────────────────────────────────────┐   │
│  │ groq_client.py      → Groq LLaMA 3.3 70B API wrapper       │   │
│  │ belief_service.py   → Bayesian belief state updates         │   │
│  │ concept_service.py  → Knowledge graph (DAG) management      │   │
│  │ curriculum_compiler.py → Greedy concept selection algorithm  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──── ML MODELS ──────────────────────────────────────────────┐   │
│  │ risk_model.py       → Random Forest risk classifier         │   │
│  │ news_model.py       → FinBERT + Random Forest trend model   │   │
│  │ category_model.py   → TF-IDF + Logistic Regression          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──── RAG ────────────────────────────────────────────────────┐   │
│  │ rag_engine.py       → 32-chunk TF-IDF knowledge retrieval   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──── INFRASTRUCTURE ─────────────────────────────────────────┐   │
│  │ db.py               → SQLite / Supabase dual-DB layer       │   │
│  │ auth.py             → Firebase JWT authentication           │   │
│  │ supabase_client.py  → Supabase PostgreSQL client            │   │
│  │ learning_db.py      → Learning module database operations   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ SQLite (Dev) │  │ Supabase     │  │ Groq Cloud   │
│ transactions │  │ (PostgreSQL) │  │ LLaMA 3.3 70B│
│ .db file     │  │ Cloud DB     │  │ LLM API      │
└──────────────┘  └──────────────┘  └──────────────┘
```

---

## 3. Technology Stack

| Layer               | Technology                                          | Version/Model              |
|---------------------|-----------------------------------------------------|----------------------------|
| **Backend Framework** | FastAPI + Uvicorn                                  | Python 3.11                |
| **Database (Dev)**    | SQLite                                             | Local file                 |
| **Database (Prod)**   | Supabase (PostgreSQL)                              | Cloud managed              |
| **Authentication**    | Firebase Admin SDK                                 | JWT token verification     |
| **ML — Risk**         | scikit-learn `RandomForestClassifier`              | 120 trees, max_depth=6     |
| **ML — Category**     | scikit-learn `TfidfVectorizer + LogisticRegression`| `category_model.joblib`    |
| **ML — Trend**        | scikit-learn `RandomForestClassifier`              | 120 trees, max_depth=5     |
| **NLP — Sentiment**   | HuggingFace Transformers `ProsusAI/finbert`        | BERT fine-tuned for finance|
| **NLP — NER**         | spaCy `en_core_web_sm`                             | CNN + transition parser    |
| **LLM**               | Groq Cloud API                                     | `llama-3.3-70b-versatile`  |
| **RAG**               | Custom TF-IDF retrieval engine                     | 32 knowledge chunks        |
| **Mobile Client**     | Android (Kotlin + Jetpack Compose)                 | Retrofit + Coroutines      |
| **Containerization**  | Docker                                              | Python 3.11-slim base      |
| **Deployment**        | Render.com / HuggingFace Spaces                    | Free tier                  |

### Python Dependencies (`requirements.txt`)
```
fastapi, uvicorn[standard], pydantic, httpx, scikit-learn, pandas,
joblib, spacy, transformers, supabase, python-dotenv, firebase-admin, groq
```

---

## 4. Backend Implementation

### 4.1 Application Entry Point (`backend/main.py`)

```python
app = FastAPI(title="Agentic Finance System", lifespan=lifespan)
```

**Startup lifecycle:**
1. **FinBERT preloading** — Background thread loads the transformer model so the first API request is fast
2. **NSE cache pre-warm** — Async task pre-fetches live market data at startup
3. **CORS middleware** — Allows all origins for mobile app connectivity
4. **6 routers registered** — transactions, finance_analysis, news_analysis, recommendation, adaptive_learning, roadmap

### 4.2 Routers

| Router                 | Prefix         | Endpoints | Purpose                                |
|------------------------|----------------|-----------|----------------------------------------|
| `transactions`         | `/api`         | 5         | SMS parsing, transaction CRUD          |
| `finance_analysis`     | `/api`         | 3         | Agent A risk analysis                  |
| `news_analysis`        | `/api`         | 4+        | Agent B sentiment/trend analysis       |
| `recommendation`       | `/api`         | 1         | Agent C RAG chatbot                    |
| `adaptive_learning`    | `/api/learning`| 4         | Bayesian micro-learning                |
| `roadmap`              | `/api/learning`| 3         | Gamified learning roadmap              |

---

## 5. Agent A — Personal Finance Analyzer

**File:** `backend/routers/finance_analysis.py`

### 5.1 Input/Output
- **Input:** `start_date`, `end_date` (date range)
- **Output:** `FinanceAnalysisResponse` containing summary, risk_level, message, ml_risk_level, ml_risk_confidence, ml_risk_explanation, ml_confidence_band, llm_insight

### 5.2 Processing Pipeline

```
User Request → Fetch Transactions by Date → Split Income/Expenses
    ↓
Credit/Debit Heuristic (15 credit + 12 debit keywords)
    ↓
Compute: total_income, total_expenses, savings, savings_rate
    ↓
Fixed vs Variable Expense Classification (by category keywords)
    ↓
Month Proration (project partial-month → full-month equivalent)
    ↓
┌─── Tier 1: Random Forest Risk Model ───┐
│ Features: income, fixed_exp, var_exp,  │
│          savings, savings_rate, var_share│
│ Output: low/medium/high + confidence   │
│ Confidence bands: high≥75%, med≥50%    │
│ Early-month downgrade logic            │
│ Human-readable explanation             │
└────────────────────────────────────────┘
    ↓
┌─── Tier 2: Groq LLM Insight ──────────┐
│ System: "Agent A, personal finance     │
│         analyst for Indian user"       │
│ Input: structured financial data       │
│ Output: 4-5 sentence data-specific     │
│         personalized insight (₹ INR)   │
└────────────────────────────────────────┘
    ↓
Log risk evaluation to risk_logs table
    ↓
Return combined response
```

### 5.3 Risk Model Technical Details

- **Algorithm:** `RandomForestClassifier(n_estimators=120, max_depth=6, class_weight="balanced_subsample")`
- **Training data:** 900 synthetic monthly profiles (300 per class)
- **Feature vector (6D):** `[income, fixed_expenses, variable_expenses, savings, savings_rate, variable_share]`
- **Labels:** `{0: "low", 1: "medium", 2: "high"}`
- **Validation:** Train/test split 75/25 with stratification
- **Saved as:** `risk_model.joblib`

### 5.4 Month Proration Logic

When the user's date range covers less than a full month, expenses are **projected** to avoid false "low risk" classifications:

```python
if coverage_ratio < 1.0 and coverage_ratio > 0:
    projected_monthly_expenses = total_expenses / coverage_ratio
```

The ML model receives projected values, and confidence bands are downgraded for early-month periods (<30% coverage → "medium" max confidence, <15% → "low" forced).

---

## 6. Agent B — News & Market Sentiment Analyzer

**File:** `backend/routers/news_analysis.py` (1,438 lines — the most complex module)

### 6.1 Data Sources

1. **Live RSS Feed:** Economic Times Market Stocks RSS (`rssfeeds/2146842.cms`)
   - Parsed with `xml.etree.ElementTree`
   - Extracts title, description, link, image_url from `<item>` elements
2. **Demo Feed Fallback:** 10 curated articles (Reliance, HDFC, TCS, Infosys, ICICI, Maruti, Tata Motors, Airtel, HCL Tech, Wipro)
3. **Live NSE Index Data:** Pre-cached at startup via `_fetch_nse_all_indices()`

### 6.2 NLP Pipeline — Multi-Layer Sentiment Analysis

```
Article Input (title + summary/body)
    ↓
┌─── Layer 1: Boilerplate Removal ────────┐
│ 13 regex patterns strip editorial noise: │
│ "buy,sell,hold?", disclaimers, ads,     │
│ cookie notices, social media CTAs       │
└─────────────────────────────────────────┘
    ↓
┌─── Layer 2: HTML Stripping ─────────────┐
│ Remove <script>, <style>, all tags      │
│ Unescape HTML entities, normalize space │
└─────────────────────────────────────────┘
    ↓
┌─── Layer 3: FinBERT NLP (Primary) ──────┐
│ Model: ProsusAI/finbert                 │
│ Tokenizer: AutoTokenizer (max_len=512) │
│ Output: probabilities for each class    │
│ Headline weight: 40%                    │
│ Body weight: 60%                        │
│ Combined: {pos, neg, neutral} probs     │
│ Score: 10 × (P(pos) − P(neg)) → [-10,+10]│
└─────────────────────────────────────────┘
    ↓ (if FinBERT unavailable)
┌─── Layer 3b: Keyword Fallback ──────────┐
│ 27 positive keywords (rally, surge...)  │
│ 31 negative keywords (crash, plunge...) │
│ 7 strong-up phrases (+3 score each)     │
│ 8 strong-down phrases (−3 score each)   │
│ Word-boundary regex matching            │
└─────────────────────────────────────────┘
    ↓
┌─── Layer 4: Entity Extraction ──────────┐
│ spaCy NER (ORG entities)                │
│ + 25+ company-sector dictionary lookup  │
│ + Keyword-based sector heuristic        │
└─────────────────────────────────────────┘
    ↓
┌─── Layer 5: Trend Prediction ───────────┐
│ Aggregate sentiment → features:         │
│ [pos_ratio, neg_ratio, neu_ratio, net]  │
│ Random Forest → bullish/sideways/bearish│
└─────────────────────────────────────────┘
    ↓
┌─── Layer 6: Impact Assessment ──────────┐
│ Percentage move extraction (regex)      │
│ Points move extraction (regex)          │
│ Confidence scoring + explanation        │
│ Trend → recommendation mapping          │
└─────────────────────────────────────────┘
    ↓
┌─── Tier 2: Groq LLM Deep Analysis ─────┐
│ System: "Agent B, financial news analyst│
│         for Indian market"              │
│ Generates market narrative + reasoning  │
└─────────────────────────────────────────┘
```

### 6.3 FinBERT Technical Details

- **Model:** `ProsusAI/finbert` (BERT fine-tuned on financial text)
- **Label mapping:** `{0: "positive", 1: "negative", 2: "neutral"}`
- **Pipeline config:** `top_k=None` (all 3 labels with scores), `truncation=True`, `max_length=512`
- **Preloading:** Background thread at app startup
- **Weighted scoring:** Headline=0.4, Body=0.6 — captures editorial framing vs detailed nuance

### 6.4 Trend Model

- **Input:** `NewsSentimentFeatures(positive_ratio, negative_ratio, neutral_ratio, net_score)`
- **Algorithm:** `RandomForestClassifier(n_estimators=120, max_depth=5)`
- **Training:** 600 synthetic sentiment regimes
- **Labels:** `{0: "bullish", 1: "sideways", 2: "bearish"}`

### 6.5 Company-Sector Mapping (25+ companies)

```python
_COMPANY_SECTORS = {
    "reliance": "Energy & Retail", "hdfc bank": "Banking",
    "tcs": "IT Services", "infosys": "IT Services",
    "maruti": "Automobile", "airtel": "Telecom",
    "itc": "FMCG", "ultratech cement": "Cement",
    "tata steel": "Metals", ...
}
```

---

## 7. Agent C — RAG Decision Synthesizer & Chatbot

**File:** `backend/routers/recommendation.py`

### 7.1 Two-Tier Pipeline

```
Input: finance_insight (Agent A) + news_insight (Agent B) + user_question + chat_history
    ↓
┌─── Tier 1: RAG Retrieval ───────────────┐
│ Build query from all inputs             │
│ TF-IDF score against 32 knowledge chunks│
│ Return top-3 relevant chunks            │
└─────────────────────────────────────────┘
    ↓
┌─── Tier 2: Groq LLM Chatbot ───────────┐
│ System: "Agent C, financial AI chatbot" │
│ Rules: specific answers, use ₹,         │
│        reference actual data, 4-8 sents │
│ Input: system_prompt + context_msg      │
│        + RAG knowledge + full history   │
│        (up to 10 turns)                 │
│ Output: conversational recommendation   │
└─────────────────────────────────────────┘
    ↓ (if LLM fails)
┌─── Deterministic Fallback ──────────────┐
│ Keyword matching on user_question:      │
│ → budget/save → 50/30/20 rule advice    │
│ → invest/sip → index fund SIP advice    │
│ → debt/loan → avalanche method          │
│ → emergency → 3-6 months rule           │
│ → risk → age-based allocation           │
│ → what-if → scenario calculation        │
│ + Append relevant RAG knowledge         │
└─────────────────────────────────────────┘
```

### 7.2 Conversation Design

The LLM receives properly structured multi-turn messages:
1. **Context injection** — "Here is your financial data..." (hidden from user)
2. **Assistant acknowledgment** — "Got it, how can I help?"
3. **Chat history** — Up to 10 previous user/assistant turns
4. **Current question** — Latest user message

This enables natural, context-aware multi-turn conversations.

---

## 8. SMS Transaction Parsing Engine

**File:** `backend/routers/transactions.py`

### 8.1 Amount Extraction — 3-Tier Regex

```python
# Tier 1: Currency prefix (Rs/INR/₹ + number)
r"(?:inr|rs\.?|rs\s*\.|₹)\s*([0-9,]+\.?[0-9]*)"

# Tier 2: Number + "rupees" suffix
r"([0-9,]+\.?[0-9]*)\s*rupees"

# Tier 3: After debit/credit keyword
r"(?:debited|credited|spent|amount|transaction)(?:\s*(?:by|for|of|:)?\s*)([0-9,]+\.?[0-9]*)"
```

**Additional fallbacks:**
- Keyword + up to 15 non-digit chars + number: `r"(?:debited|credited)\D{0,15}([0-9,]+\.?[0-9]*)"`
- Last resort: any standalone 2+ digit number

### 8.2 Spam Filtering

```python
_SPAM_KEYWORDS = ["cashback offer", "subscribe now", "otp", "verification code", ...]
_BANK_SIGNALS  = ["a/c", "upi", "neft", "imps", "bank", "bal", "xxxx", ...]
```

A message is treated as a genuine transaction only if:
1. It contains a recognizable money amount AND
2. It has a debit/credit keyword OR a bank signal term AND
3. It is NOT clearly spam (≥2 spam keywords AND no bank signals)

### 8.3 Category Classification — Hybrid Rule+ML

1. **Rule-based:** 10 category keyword maps (70+ keywords total)
2. **ML refinement:** `category_model.joblib` (TF-IDF + Logistic Regression) with confidence threshold fallback

### 8.4 Credit/Debit Detection

- **15 credit keywords:** credited, salary, payment received, refund, cashback, reversal, deposit, neft/imps/upi credit
- **12 debit keywords:** debited, spent, purchase, payment made, upi payment, sent to, withdrawn, atm wdl

---

## 9. ML Models — Training & Inference

### 9.1 Category Model (`category_model.py`)

| Property        | Value                                |
|-----------------|--------------------------------------|
| Algorithm       | TF-IDF Vectorizer + Logistic Regression |
| Training Data   | Synthetic SMS-like messages (`generate_synthetic_dataset.py`) |
| Categories      | 10 (Food & Dining, Transport, Shopping, Bills, Groceries, Entertainment, Health, Education, Investment, Other) |
| Inference       | `predict_category(text, default, min_confidence=0.5)` |
| Fallback        | Returns rule-based default if confidence < threshold |
| Saved As        | `category_model.joblib` (2.3 MB)    |

### 9.2 Risk Model (`risk_model.py`)

| Property        | Value                                |
|-----------------|--------------------------------------|
| Algorithm       | RandomForestClassifier               |
| Hyperparameters | n_estimators=120, max_depth=6, class_weight="balanced_subsample" |
| Training Data   | 900 synthetic monthly profiles (300 per class) |
| Features (6D)   | income, fixed_expenses, variable_expenses, savings, savings_rate, variable_share |
| Labels          | low (0), medium (1), high (2)        |
| Validation      | Stratified 75/25 train/test split    |
| Saved As        | `risk_model.joblib` (404 KB)         |

### 9.3 News Trend Model (`news_model.py`)

| Property        | Value                                |
|-----------------|--------------------------------------|
| Algorithm       | RandomForestClassifier               |
| Hyperparameters | n_estimators=120, max_depth=5, class_weight="balanced_subsample" |
| Training Data   | 600 synthetic sentiment regimes (200 per class) |
| Features (4D)   | positive_ratio, negative_ratio, neutral_ratio, net_score |
| Labels          | bullish (0), sideways (1), bearish (2) |
| Saved As        | `news_trend_model.joblib` (232 KB)   |

---

## 10. NLP Pipeline — FinBERT & spaCy

### 10.1 FinBERT Sentiment Pipeline

```python
# Model: ProsusAI/finbert (BERT fine-tuned for financial domain)
pipeline("sentiment-analysis",
         model="ProsusAI/finbert",
         top_k=None,  # All 3 labels with probabilities
         truncation=True, max_length=512)
```

**Two inference modes:**
1. `finbert_sentiment(texts)` → `[(label, confidence), ...]` — Top-label only
2. `finbert_sentiment_detailed(texts)` → `[{"positive": p, "negative": n, "neutral": u}, ...]` — Full distribution

**Weighted combination for article analysis:**
- Headline sentiment weight: **0.4** (editorial framing)
- Body sentiment weight: **0.6** (nuance and detail)
- Combined score: `10 × (P(positive) − P(negative))` → continuous scale [-10, +10]

### 10.2 spaCy NER

- **Model:** `en_core_web_sm`
- **Usage:** Extract ORG entities from news titles/articles
- **Filtering:** Rejects short names (<3 chars), code-like tokens (function, http, script, cookie), and non-alphanumeric-heavy strings
- **Fallback:** If spaCy is unavailable, uses the `_COMPANY_SECTORS` dictionary for keyword matching

---

## 11. RAG Knowledge Engine

**File:** `backend/rag/rag_engine.py`

### 11.1 Knowledge Corpus

**32 curated `KnowledgeChunk` entries** organized into 10 topics:

| Topic              | Chunks | Coverage                                        |
|--------------------|--------|-------------------------------------------------|
| Emergency Funds    | 2      | 3-6 month rule, priority over investing         |
| Budgeting          | 3      | 50/30/20 rule, expense tracking, cutting spending|
| Debt Management    | 3      | Avalanche, snowball, credit card trap            |
| Investing Basics   | 4      | SIP, diversification, index vs active, compounding|
| Risk Management    | 3      | Risk tolerance, market crashes, position sizing  |
| Tax & Insurance    | 3      | Section 80C, health insurance, term life         |
| Indian Market      | 4      | UPI safety, FD vs debt funds, Nifty returns, gold|
| Behavioral Finance | 2      | Emotional investing, lifestyle inflation trap    |
| Goals & Planning   | 2      | Goal-based investing, retirement in 20s          |
| Scams & Selection  | 2      | Scam red flags, mutual fund criteria             |
| Income             | 2      | Salary optimization, side income ideas           |
| Real Estate        | 2      | Rent vs buy, home loan tips                      |

### 11.2 TF-IDF Retrieval Algorithm

```python
def retrieve(query: str, k: int = 3) -> List[KnowledgeChunk]:
    # 1. Tokenize query (lowercase, remove stop words, remove punctuation)
    # 2. For each chunk, compute TF-IDF dot product:
    #    score += query_TF × IDF × chunk_TF × IDF
    # 3. Add tag bonus: +3.0 per exact tag match
    # 4. Sort by score, return top-k (filter out zero-score)
    # 5. Fallback: return first k chunks if nothing matched
```

- **IDF computation:** Smoothed log IDF: `log((N+1)/(df+1)) + 1`
- **Stop words:** 80+ common English stop words filtered out
- **Tag bonus:** Curated tags get 3× score multiplier for precise matching

---

## 12. Adaptive Micro-Learning System

**Files:** `backend/routers/adaptive_learning.py`, `backend/services/belief_service.py`, `backend/services/concept_service.py`, `backend/services/curriculum_compiler.py`

### 12.1 Knowledge Graph (DAG)

10 financial concepts across 4 difficulty levels with prerequisite dependencies:

```
Level 1: money_basics ──────────────────────┐
         income_basics ──┐                  │
                         │                  │
Level 2:          budgeting_basics ◄─── expense_tracking ◄── money_basics
                   (requires both)
                  ┌──────┼──────┐
                  ▼      ▼      ▼
Level 3:    emergency  debt    saving
            _fund    _mgmt   _strategies
              │        │        │
              │        ▼        │
Level 4:      │   credit_score  │
              │                 │
              ▼                 ▼
         investment_basics  financial_goals
```

**Cycle detection:** DFS-based validation at graph construction time

### 12.2 Bayesian Knowledge Tracing

Three-state belief model per user per concept:
- `P(Unknown)` — User doesn't know the concept
- `P(Partial)` — User partially understands
- `P(Mastered)` — User has mastered the concept

**Constraint:** `P(Unknown) + P(Partial) + P(Mastered) = 1.0` (enforced by Pydantic validator + normalization)

**Update rules after quiz answer:**

| Scenario            | Unknown Shift | Partial Shift | Mastered Shift |
|---------------------|---------------|---------------|----------------|
| Fast correct (<30s) | -0.4          | +0.1          | +0.3           |
| Slow correct (>30s) | -0.3          | +0.15         | +0.15          |
| Fast incorrect      | +0.2          | +0.1          | -0.3           |
| Slow incorrect (>60s)| +0.4         | -0.2          | -0.2           |

**Mastery levels:** mastered (P>0.6), partial (P>0.5), unknown (default)

### 12.3 Curriculum Compiler

Greedy optimization with explainability:

```
score = readiness × urgency × relevance

readiness = min(mastery of all prerequisites)  [threshold: 0.6]
urgency   = 1 − P(Mastered)
relevance = context-based multiplier:
  • high risk user    → budget/emergency concepts ×1.5
  • increasing spend  → expense tracking ×1.3
  • low savings       → saving strategies ×1.4
  • has debt          → debt management ×1.6
```

### 12.4 AI Card Generation (Groq)

1. **Content generation:** 150-200 word micro-learning text with practical examples
2. **Quiz generation:** 4-option MCQ with explanation, using JSON structured output
3. **Caching:** Cards cached by concept_id; cache invalidated when mastery level changes
4. **Background pre-generation:** Next card is pre-generated asynchronously after answer submission

---

## 13. Gamified Learning Roadmap

**File:** `backend/routers/roadmap.py`

### 13.1 Lesson Structure

| Lesson | Title             | Difficulty   | Cards | Prerequisites |
|--------|-------------------|-------------|-------|---------------|
| 1      | Financial Basics  | Beginner    | 5     | None          |
| 2      | Budgeting Mastery | Beginner    | 7     | Lesson 1      |
| 3      | Emergency Fund    | Intermediate| 6     | Lesson 2      |
| 4      | Debt Management   | Intermediate| 8     | Lesson 3      |
| 5      | Investment Basics | Advanced    | 10    | Lesson 4      |

### 13.2 Gamification System

- **Points:** 50 base + 100 perfect score bonus + 20 speed bonus (<5 min)
- **Stars:** ★★★ (≥90%), ★★ (≥70%), ★ (≥50%)
- **Achievements:** First Steps 🔥, Bookworm 📚, Perfect Score 🎯, Speed Learner ⚡, Week Warrior 🏆, Master 💎
- **Daily Goals:** Lessons completed (target: 1), Questions answered (10), Minutes spent (15)
- **Lesson States:** completed, current, locked, future (based on prerequisite satisfaction)

---

## 14. Database Layer

**File:** `backend/db.py`

### 14.1 Dual-Database Architecture

```python
USE_SUPABASE = bool(os.getenv("SUPABASE_URL"))
# If Supabase is configured → use PostgreSQL
# Otherwise → fallback to local SQLite file
# If Supabase call fails → automatic SQLite fallback
```

### 14.2 Schema

**`transactions` table:**
```sql
CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL DEFAULT 'default_user',
    amount REAL NOT NULL,
    merchant TEXT,
    category TEXT,
    currency TEXT,
    timestamp TEXT NOT NULL,
    raw_message TEXT NOT NULL
);
CREATE INDEX idx_transactions_user ON transactions(user_id);
```

**`risk_logs` table:**
```sql
CREATE TABLE risk_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL DEFAULT 'default_user',
    created_at TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    total_income REAL NOT NULL,
    total_expenses REAL NOT NULL,
    savings REAL NOT NULL,
    heuristic_risk TEXT NOT NULL,
    ml_risk_level TEXT,
    ml_risk_confidence REAL
);
```

**`belief_states` table:**
```sql
CREATE TABLE belief_states (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL, concept_id TEXT NOT NULL,
    belief_unknown REAL DEFAULT 0.8,
    belief_partial REAL DEFAULT 0.15,
    belief_mastered REAL DEFAULT 0.05,
    interaction_count INTEGER DEFAULT 0,
    last_interaction TIMESTAMP,
    UNIQUE(user_id, concept_id)
);
```

**`interaction_events` table:**
```sql
CREATE TABLE interaction_events (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL, card_id TEXT NOT NULL,
    concept_id TEXT NOT NULL, answer_index INTEGER NOT NULL,
    is_correct BOOLEAN NOT NULL, time_spent_seconds INTEGER NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 14.3 Data Access Functions

| Function                        | DB Support         | Description                            |
|---------------------------------|--------------------|----------------------------------------|
| `get_user_transactions()`       | Supabase + SQLite  | Fetch recent transactions (amount > 0) |
| `get_user_transactions_by_date()` | Supabase + SQLite | Fetch by date range                    |
| `insert_transaction()`          | Supabase + SQLite  | Insert new transaction                 |
| `insert_risk_log()`             | Supabase + SQLite  | Log risk evaluation                    |
| `delete_user_transactions()`    | Supabase + SQLite  | Clear all user transactions            |
| `update_transaction_category()` | Supabase + SQLite  | Update category                        |

---

## 15. Authentication System

**File:** `backend/auth.py`

- **Provider:** Firebase Admin SDK
- **Token type:** Bearer JWT tokens
- **Initialization:** Supports two modes:
  1. `FIREBASE_ADMIN_SDK_JSON` env var (full JSON — for cloud deployment)
  2. `firebase-admin-sdk.json` file path (for local development)
- **Fallback:** If Firebase is not configured, returns `"default_user"` for development
- **Two dependency functions:**
  - `get_current_user()` — Required auth (returns user_id or "default_user")
  - `get_optional_user()` — Optional auth (returns user_id or None)

---

## 16. Android Mobile Application

### 16.1 Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose + XML layouts
- **Networking:** Retrofit + Gson + Kotlin Coroutines (async)
- **Auth:** Firebase Authentication (`AuthManager.kt`)

### 16.2 Key Components (30 Kotlin files)

| File                            | Purpose                                    |
|---------------------------------|--------------------------------------------|
| `MainActivity.kt`              | Main activity with navigation              |
| `LoginScreen.kt`               | Firebase login/signup UI                   |
| `HomeFragment.kt`              | Dashboard with transaction history         |
| `LearnFragment.kt`             | Learning module entry                      |
| `ReelsStyleLearningScreen.kt`  | Swipeable micro-learning card UI           |
| `LearningRoadmapScreen.kt`     | Gamified lesson roadmap UI                 |
| `NotificationListener.kt`      | `NotificationListenerService` for auto-capture |
| `NotificationPermissionHelper.kt` | Permission request handling             |
| `ApiClient.kt`                 | Retrofit singleton (`http://10.0.2.2:8000`) |
| `FinanceApi.kt`                | API interface definitions                  |
| `LearningRoadmapApi.kt`        | Learning API interface                     |
| `ParseMessageRequestDto.kt`    | Request DTO for SMS parsing                |
| `TransactionDto.kt`            | Transaction response DTO                   |
| `ChatTurnDto.kt`               | Chat message DTO                           |
| `SynthesizeRequestDto.kt`      | Agent C request DTO                        |
| `SynthesizeResponseDto.kt`     | Agent C response DTO                       |
| `LearningModels.kt`            | Learning card + quiz data models           |
| `RoadmapModels.kt`             | Roadmap + achievement data models          |

### 16.3 Notification Auto-Capture

```kotlin
class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getString("android.title")
        val text = sbn.notification.extras.getString("android.text")
        val rawMessage = "$title $text"
        // Send to /api/parse_message in background coroutine
    }
}
```

---

## 17. API Reference

### Transaction APIs

| Method | Endpoint                          | Auth | Description                          |
|--------|-----------------------------------|------|--------------------------------------|
| POST   | `/api/parse_message`              | Yes  | Parse raw SMS → structured transaction |
| GET    | `/api/transactions`               | Yes  | List recent transactions (limit param)|
| PUT    | `/api/transactions/{id}/category` | Yes  | Update transaction category           |
| DELETE | `/api/transactions`               | Yes  | Clear all user transactions           |
| GET    | `/api/health`                     | No   | Health check                          |

### Agent A APIs

| Method | Endpoint                  | Auth | Description                              |
|--------|---------------------------|------|------------------------------------------|
| POST   | `/api/analyze_finance`    | Yes  | Risk analysis + LLM insight for date range|
| POST   | `/api/expense_breakdown`  | Yes  | Category-wise expense aggregation        |
| GET    | `/api/risk_logs_summary`  | Yes  | Historical risk evaluation stats         |

### Agent B APIs

| Method | Endpoint                  | Auth | Description                              |
|--------|---------------------------|------|------------------------------------------|
| POST   | `/api/analyze_news`       | No   | Topic sentiment + trend analysis         |
| GET    | `/api/news_feed`          | No   | Live/demo news feed articles             |
| POST   | `/api/analyze_article`    | No   | Deep single-article NLP analysis         |
| GET    | `/api/live_market`        | No   | Real-time NSE market indices             |

### Agent C API

| Method | Endpoint           | Auth | Description                              |
|--------|--------------------|------|------------------------------------------|
| POST   | `/api/synthesize`  | No   | RAG + LLM multi-turn chatbot             |

### Learning APIs

| Method | Endpoint                         | Auth | Description                              |
|--------|----------------------------------|------|------------------------------------------|
| GET    | `/api/learning/next-card`        | Yes  | Bayesian-selected micro-learning card    |
| POST   | `/api/learning/submit-answer`    | Yes  | Submit quiz answer + belief update       |
| GET    | `/api/learning/progress`         | Yes  | Overall learning progress                |
| GET    | `/api/learning/explanation`      | Yes  | Concept selection explanation            |
| GET    | `/api/learning/roadmap`          | Yes  | Gamified lesson roadmap                  |
| POST   | `/api/learning/complete-lesson`  | Yes  | Complete lesson + award points           |
| GET    | `/api/learning/stats`            | Yes  | User learning statistics                 |

---

## 18. Deployment

### 18.1 Docker

```dockerfile
FROM python:3.11-slim
# Install PyTorch CPU, all Python deps, spaCy
# Copy backend/ code
EXPOSE 7860
CMD ["python", "-m", "uvicorn", "backend.main:app", "--host", "0.0.0.0", "--port", "7860"]
```

### 18.2 Render.com (`render.yaml`)

```yaml
services:
  - type: web
    name: agentic-finance-backend
    runtime: docker
    plan: free
    envVars:
      - SUPABASE_URL, SUPABASE_SERVICE_KEY
      - GROQ_API_KEY
      - FIREBASE_ADMIN_SDK_JSON
```

### 18.3 Environment Variables

| Variable              | Required | Description                          |
|-----------------------|----------|--------------------------------------|
| `SUPABASE_URL`        | No       | Supabase project URL (enables cloud DB) |
| `SUPABASE_SERVICE_KEY` | No      | Supabase service role key            |
| `GROQ_API_KEY`        | Yes*     | Groq API key for LLM features       |
| `FIREBASE_ADMIN_SDK_JSON` | No   | Firebase credentials JSON            |

*LLM features gracefully degrade without Groq key — all Tier 1 ML features still work.

---

## 19. Project Structure

```
agentic-finance-system/
├── backend/
│   ├── main.py                    # FastAPI app entrypoint + startup lifecycle
│   ├── db.py                      # SQLite / Supabase dual-DB layer
│   ├── auth.py                    # Firebase JWT authentication
│   ├── supabase_client.py         # Supabase PostgreSQL client singleton
│   ├── learning_db.py             # Learning module DB operations
│   ├── risk_model.py              # Random Forest risk classifier
│   ├── news_model.py              # FinBERT NLP + trend model
│   ├── category_model.py          # TF-IDF + Logistic Regression
│   ├── risk_model.joblib           # Trained risk model (404 KB)
│   ├── news_trend_model.joblib     # Trained trend model (232 KB)
│   ├── category_model.joblib       # Trained category model (2.3 MB)
│   ├── requirements.txt           # Python dependencies
│   ├── routers/
│   │   ├── transactions.py        # SMS parsing + transaction CRUD
│   │   ├── finance_analysis.py    # Agent A: risk ML + LLM insight
│   │   ├── news_analysis.py       # Agent B: FinBERT NLP + trend + LLM
│   │   ├── recommendation.py      # Agent C: RAG + LLM chatbot
│   │   ├── adaptive_learning.py   # Bayesian learning + AI cards
│   │   └── roadmap.py             # Gamified learning roadmap
│   ├── services/
│   │   ├── groq_client.py         # Groq LLaMA 3.3 70B API client
│   │   ├── belief_service.py      # Bayesian belief state management
│   │   ├── concept_service.py     # Knowledge graph (DAG) management
│   │   └── curriculum_compiler.py # Greedy concept selection
│   ├── models/
│   │   ├── transaction_models.py  # Transaction Pydantic models
│   │   ├── learning.py            # Learning system data models
│   │   └── roadmap.py             # Roadmap data models
│   ├── rag/
│   │   └── rag_engine.py          # 32-chunk TF-IDF knowledge retrieval
│   ├── learning/
│   │   ├── concepts.json          # Financial concept definitions
│   │   └── curriculum_engine.py   # Curriculum engine
│   ├── data/                      # Training data files
│   └── tests/                     # pytest test suite
├── android/
│   ├── README.md                  # Android architecture documentation
│   └── app/                       # Kotlin source (30 files)
│       └── src/main/java/com/example/agenticfinance/
│           ├── MainActivity.kt, LoginScreen.kt
│           ├── HomeFragment.kt, LearnFragment.kt
│           ├── ReelsStyleLearningScreen.kt
│           ├── LearningRoadmapScreen.kt
│           ├── NotificationListener.kt
│           ├── ApiClient.kt, FinanceApi.kt
│           └── ... (DTOs, models, theme)
├── docs/
│   ├── architecture.md            # Architecture overview
│   ├── problem_statement.md       # Problem statement
│   ├── tech_stack.md              # Technology stack
│   └── roadmap.md                 # Development phases
├── Dockerfile                     # Docker containerization
├── render.yaml                    # Render.com deployment config
├── README.md                      # Project overview
├── README_PRESENTATION.md         # PPT outline (this companion)
└── README_TECHNICAL.md            # This file
```

---

## 20. Design Patterns & SE Principles

| Pattern/Principle           | Implementation                                          |
|-----------------------------|---------------------------------------------------------|
| **Multi-Agent Architecture** | 3 autonomous agents (A/B/C) with clear separation     |
| **Two-Tier Pipeline**        | ML (Tier 1) + LLM (Tier 2) with graceful fallback    |
| **Singleton Pattern**        | GroqClient, ConceptGraph, FinBERT pipeline, Supabase  |
| **Strategy Pattern**         | Dual database (SQLite ↔ Supabase) with auto-failover  |
| **Observer Pattern**         | Android NotificationListenerService                    |
| **DAG Pattern**              | Concept prerequisite graph with DFS cycle detection    |
| **Bayesian Inference**       | Probabilistic knowledge state updates                  |
| **RAG Architecture**         | Retrieval-Augmented Generation with TF-IDF scoring     |
| **SOLID Principles**         | Single responsibility per router, open for extension   |
| **Dependency Injection**     | FastAPI `Depends()` for auth and user context          |
| **Graceful Degradation**     | Every LLM feature has deterministic fallback           |
| **Background Tasks**         | FinBERT preload, NSE cache, card pre-generation        |
| **Caching**                  | Learning cards, NSE data, FinBERT model, concept graph |

---

## 21. How to Run

### Local Development

```powershell
# 1. Navigate to project
cd "c:\Users\jaini\OneDrive\Desktop\SEM-6\Software Engg\agentic-finance-system"

# 2. Install dependencies
python -m pip install -r backend/requirements.txt

# 3. Set environment variables (create backend/.env)
# GROQ_API_KEY=your_key_here
# SUPABASE_URL=your_url (optional)
# SUPABASE_SERVICE_KEY=your_key (optional)

# 4. Run the server
uvicorn backend.main:app --reload

# Server: http://127.0.0.1:8000
# API docs: http://127.0.0.1:8000/docs
```

### Docker

```bash
docker build -t agentic-finance .
docker run -p 7860:7860 --env-file backend/.env agentic-finance
```

### Training ML Models (Optional)

```powershell
cd backend
python generate_synthetic_dataset.py   # Generate training data
python train_category_model.py         # Train category classifier
python risk_model.py                   # Train risk model (auto-generates data)
python train_news_trend_model.py       # Train trend model
```

### Running Tests

```powershell
pytest backend/tests/test_api.py
```

---

> **Built with:** FastAPI · scikit-learn · HuggingFace Transformers (FinBERT) · spaCy · Groq (LLaMA 3.3 70B) · Supabase · Firebase · Docker · Kotlin · Jetpack Compose
