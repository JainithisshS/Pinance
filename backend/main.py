import os, warnings

# Suppress noisy TensorFlow / oneDNN / HuggingFace warnings
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"          # hide TF INFO + WARNING
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", message=".*deprecated.*")

from contextlib import asynccontextmanager
from threading import Thread

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .routers import (
    transactions,
    finance_analysis,
    news_analysis,
    recommendation,
    adaptive_learning,  # New adaptive learning system
    roadmap,  # Learning roadmap with gamification
)


def _preload_nlp_models() -> None:
    """Preload heavy NLP models in a background thread at startup."""
    try:
        from .news_model import _get_sentiment_pipeline
        _get_sentiment_pipeline()
    except Exception as exc:
        print(f"[startup] FinBERT preload warning: {exc}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Kick off FinBERT loading in background so it's ready by first request
    t = Thread(target=_preload_nlp_models, daemon=True)
    t.start()
    print("[startup] FinBERT preloading in background thread...")
    # Pre-warm NSE market data cache
    import asyncio
    asyncio.create_task(_prewarm_nse_cache())
    yield


async def _prewarm_nse_cache():
    """Pre-fetch NSE data at startup so first API call is instant."""
    try:
        from .routers.news_analysis import _fetch_nse_all_indices
        data = await _fetch_nse_all_indices()
        print(f"[startup] NSE cache pre-warmed: {len(data or [])} indices")
    except Exception as exc:
        print(f"[startup] NSE pre-warm failed: {exc}")


app = FastAPI(title="Agentic Finance System", lifespan=lifespan)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(transactions.router, prefix="/api", tags=["transactions"])
app.include_router(finance_analysis.router, prefix="/api", tags=["finance-analysis"])
app.include_router(news_analysis.router, prefix="/api", tags=["news-analysis"])
app.include_router(recommendation.router, prefix="/api", tags=["recommendation"])
# Old static learning system removed - replaced by adaptive_learning
app.include_router(adaptive_learning.router)  # New adaptive micro-learning system
app.include_router(roadmap.router)  # Learning roadmap with gamification


@app.get("/")
async def root():
    return {"message": "Agentic Finance System Backend is running"}

# Trigger reload
