from fastapi.testclient import TestClient

from ..main import app


client = TestClient(app)


def test_health_check():
    response = client.get("/api/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_parse_message_basic():
    payload = {
        "raw_message": "INR 500.00 spent at Swiggy on your card",
    }

    response = client.post("/api/parse_message", json=payload)
    assert response.status_code == 200

    data = response.json()

    assert data["amount"] == 500.0
    assert data["category"] == "Food & Dining"
    assert isinstance(data["id"], int)


def test_analyze_finance_empty_range():
    # Use a date range far in the past to ensure no data
    payload = {
        "start_date": "2000-01-01",
        "end_date": "2000-01-31",
    }

    response = client.post("/api/analyze_finance", json=payload)
    assert response.status_code == 200

    data = response.json()

    assert data["summary"]["total_spent"] == 0.0
    assert data["summary"]["transactions_count"] == 0
    assert data["risk_level"] == "low"


def test_analyze_news_structure():
    payload = {"topic": "stocks"}

    response = client.post("/api/analyze_news", json=payload)
    assert response.status_code == 200

    data = response.json()

    assert data["topic"] == "stocks"
    assert data["overall_sentiment"] in {"positive", "negative", "neutral"}
    assert "sentiment_breakdown" in data
    assert "sample_articles" in data and isinstance(data["sample_articles"], list)


def test_synthesize_includes_rag_knowledge():
    payload = {
        "finance_insight": "high expenses and no emergency fund",
        "news_insight": "markets are volatile",
    }

    response = client.post("/api/synthesize", json=payload)
    assert response.status_code == 200

    data = response.json()

    assert "retrieved_knowledge" in data
    assert isinstance(data["retrieved_knowledge"], list)
    assert len(data["retrieved_knowledge"]) >= 1
