# Agentic Finance System

An AI-powered personal finance and market insight platform with a multi-agent backend, Android notification capture, retrieval-augmented answers, and adaptive financial learning.

## App Images

<p align="center">
	<img src="android/app/src/main/app/src/main/res/drawable/pinance_logo.png" alt="Agentic Finance System logo" width="220" />
</p>

The screenshots below are shown in the order they were captured from the `pinance` folder.

<table>
	<tr>
		<td align="center" width="33%">
			<img src="assets/app-screens/02-login-screen.jpeg" alt="Login screen" width="100%" />
			<br /><sub>2. Login screen</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/01-google-accounts.jpeg" alt="Google accounts" width="100%" />
			<br /><sub>1. Google accounts</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/09-asking-notification-access.jpeg" alt="Asking notification access" width="100%" />
			<br /><sub>9. Asking notification access</sub>
		</td>
	</tr>
	<tr>
		<td align="center" width="33%">
			<img src="assets/app-screens/07-dashboard-home.jpeg" alt="Dashboard home" width="100%" />
			<br /><sub>7. Dashboard home</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/06-news-dashboard-market-sentiment.jpeg" alt="News dashboard with market sentiment" width="100%" />
			<br /><sub>6. News dashboard with market sentiment</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/03-news-sector-pulse-headlines.jpeg" alt="News dashboard with sector pulse and headlines" width="100%" />
			<br /><sub>3. News dashboard with sector pulse and headlines</sub>
		</td>
	</tr>
	<tr>
		<td align="center" width="33%">
			<img src="assets/app-screens/04-detailed-news.jpeg" alt="Detailed news" width="100%" />
			<br /><sub>4. Detailed news</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/05-news-analysis.jpeg" alt="News analysis" width="100%" />
			<br /><sub>5. News analysis</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/08-finance-dashboard.jpeg" alt="Finance dashboard" width="100%" />
			<br /><sub>8. Finance dashboard</sub>
		</td>
	</tr>
	<tr>
		<td align="center" width="33%">
			<img src="assets/app-screens/10-what-if-simulator-1.jpeg" alt="What if simulator 1" width="100%" />
			<br /><sub>10. What if simulator</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/11-what-if-simulator-2.jpeg" alt="What if simulator 2" width="100%" />
			<br /><sub>11. What if simulator</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/12-agent-c-chatbot.jpeg" alt="Agent C chatbot" width="100%" />
			<br /><sub>12. Agent C chatbot</sub>
		</td>
	</tr>
	<tr>
		<td align="center" width="33%">
			<img src="assets/app-screens/15-learning-page-1.jpeg" alt="Learning page 1" width="100%" />
			<br /><sub>15. Learning page</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/13-learning-page-2.jpeg" alt="Learning page 2" width="100%" />
			<br /><sub>13. Learning page</sub>
		</td>
		<td align="center" width="33%">
			<img src="assets/app-screens/14-learning-page-3.jpeg" alt="Learning page 3" width="100%" />
			<br /><sub>14. Learning page</sub>
		</td>
	</tr>
</table>

## What It Does

Agentic Finance System turns raw bank and UPI notifications, live market news, and curated finance knowledge into practical guidance.

- The Android app captures notification text and forwards it to the backend.
- The backend parses transactions, classifies spending, estimates financial risk, and analyzes market sentiment.
- A RAG-powered chatbot grounds answers in a curated finance knowledge base.
- An adaptive learning engine tracks concept mastery and serves personalized finance lessons.

## Why This Project Exists

Most finance tools solve only one part of the problem:

- Expense trackers store transactions but do not explain spending behavior.
- Market apps show news but do not connect it to personal finances.
- Robo-advisors focus on investments and overlook day-to-day spending patterns.
- Static learning apps do not adapt to a user's actual financial profile.

This project combines those pieces into one system that is explainable, personalized, and reliable even when external AI services are unavailable.

## System Overview

The report describes a three-agent architecture running on a FastAPI backend:

- Agent A: personal finance analysis from SMS and UPI notifications
- Agent B: news and market intelligence using FinBERT, NER, and trend prediction
- Agent C: grounded decision support with RAG and chat history

Each agent uses a two-tier pipeline:

- Tier 1: deterministic rule-based or ML inference
- Tier 2: LLM reasoning through Groq LLaMA 3.3 70B with fallback to Tier 1

The system also includes a Bayesian Knowledge Tracing learning engine for finance education.

## System Architecture Flow Chart

```mermaid
flowchart TD
	U[User]
	A[Android App\nKotlin + Jetpack Compose]
	N[Notification Listener\nSMS / UPI Capture]
	API[FastAPI Backend\nPython 3.11]

	RA[Agent A\nFinance Analyzer]
	RB[Agent B\nNews + Market Intelligence]
	RC[Agent C\nDecision Synthesizer + RAG]
	RL[Adaptive Learning Engine\nBayesian Knowledge Tracing]

	DB[(SQLite / Supabase PostgreSQL)]
	KG[(RAG Knowledge Base)]
	LLM[Groq LLaMA 3.3 70B]
	EXT[External Data\nRSS + Market Feeds]

	U --> A
	A --> N
	A --> API
	N --> API

	API --> RA
	API --> RB
	API --> RC
	API --> RL

	RA --> DB
	RB --> EXT
	RB --> DB
	RC --> KG
	RC --> DB
	RL --> DB

	RA --> LLM
	RB --> LLM
	RC --> LLM

	LLM --> API
	DB --> API
	API --> A
	A --> U
```

## Key Features

- Automatic SMS and UPI notification parsing
- Amount, merchant, and debit/credit detection from raw text
- Transaction categorization with keyword rules and ML fallback
- Personal financial risk scoring over a date range
- Live financial news sentiment analysis
- Market trend prediction from aggregated sentiment signals
- Grounded finance recommendations with retrieved context and chat history
- Adaptive learning cards, roadmap progression, and mastery tracking

## Architecture Highlights

- Android client: Kotlin, Jetpack Compose, NotificationListenerService, Retrofit, Coroutines
- API backend: FastAPI, Python 3.11, Uvicorn
- Persistence: SQLite for development, Supabase PostgreSQL for production
- Authentication: Firebase Admin SDK
- NLP and ML: FinBERT, spaCy, scikit-learn, Transformers, PyTorch
- Retrieval and chat: TF-IDF RAG with Groq LLaMA 3.3 70B
- Deployment: Docker and Render support

## Repository Layout

- [backend/](backend/) - FastAPI app, routers, models, RAG, learning, and ML helpers
- [android/](android/) - Android client notes and integration details
- [docs/](docs/) - Architecture, roadmap, problem statement, and stack notes
- [presentation/](presentation/) - Presentation HTML for the project demo
- [scripts/](scripts/) - Helper scripts for APK and backend workflows

## Local Setup

From PowerShell on Windows:

```powershell
cd "c:\Users\jaini\OneDrive\Desktop\SEM-6\Software Engg\agentic-finance-system"
python -m pip install -r backend/requirements.txt
uvicorn backend.main:app --reload
```

The backend runs at http://127.0.0.1:8000.

## Main API Endpoints

- `GET /` - Service status
- `GET /api/health` - Backend health check
- `POST /api/parse_message` - Parse and store a transaction
- `GET /api/transactions` - List stored transactions
- `POST /api/analyze_finance` - Summarize spending and risk
- `POST /api/analyze_news` - Analyze market and news sentiment
- `GET /api/live_market` - Fetch the current market snapshot
- `POST /api/synthesize` - Generate grounded finance guidance
- `GET /api/learning/next-card` - Fetch the next learning card

## Tests

Run backend tests from the repo root:

```powershell
pytest backend/tests/test_api.py
```

## Model Utilities

To regenerate the synthetic dataset and retrain the category model:

```powershell
cd "c:\Users\jaini\OneDrive\Desktop\SEM-6\Software Engg\agentic-finance-system\backend"
python generate_synthetic_dataset.py
python train_category_model.py
```

If `backend/category_model.joblib` exists, the parser uses it automatically.

## Deployment

- Docker support is provided through the root `Dockerfile`
- `render.yaml` is included for Render deployment
- The backend also contains the Supabase schema and Firebase auth setup needed for production deployment

## Supporting Docs

- [backend/README.md](backend/README.md) - Backend-focused notes
- [android/README.md](android/README.md) - Android client notes
- [docs/roadmap.md](docs/roadmap.md) - Phase-by-phase roadmap
- [README_PRESENTATION.md](README_PRESENTATION.md) - Presentation notes
- [report_ieee_8p.tex](report_ieee_8p.tex) - Main IEEE-style project report source

## Scope

This repository centers on the backend, Android integration, and supporting documentation. Generated reports and presentation artifacts are kept in the workspace unless explicitly required elsewhere.
