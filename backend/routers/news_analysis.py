import os
import re
import xml.etree.ElementTree as ET
from html import unescape
from typing import Optional

import httpx
from fastapi import APIRouter
from pydantic import BaseModel

from ..news_model import NewsSentimentFeatures, finbert_sentiment, finbert_sentiment_detailed, predict_trend
from ..services.groq_client import get_groq_client

try:  # spaCy is optional; fall back gracefully if missing
    import spacy
    from spacy.language import Language
except Exception:  # pragma: no cover - defensive import
    spacy = None
    Language = None


router = APIRouter()


class NewsAnalysisRequest(BaseModel):
    topic: str


class NewsArticle(BaseModel):
    title: str
    source: str
    url: str | None = None


class NewsSentimentBreakdown(BaseModel):
    positive: int
    negative: int
    neutral: int


class NewsAnalysisResponse(BaseModel):
    topic: str
    overall_sentiment: str
    sentiment_breakdown: NewsSentimentBreakdown
    sample_articles: list[NewsArticle]
    summary: str
    trend_label: str | None = None
    trend_confidence: float | None = None
    dominant_sector: str | None = None
    llm_summary: str | None = None  # LLM-generated market narrative


class NewsFeedArticle(BaseModel):
    """Article structure for the simple news feed shown in the app."""

    id: int
    title: str
    source: str
    summary: str
    url: str | None = None
    image_url: str | None = None


class ArticleAnalysisRequest(BaseModel):
    """Request to analyze a specific news item from the feed."""

    title: str
    summary: str
    url: str | None = None


class ArticleAnalysisResponse(BaseModel):
    """Result of analyzing a single company/news article."""

    company: str
    sector: str
    trend: str
    recommendation: str
    reasoning: str
    confidence_score: float
    confidence_level: str
    confidence_explanation: str
    impact_strength: str
    llm_reasoning: str | None = None  # LLM-generated deep analysis


_POSITIVE_KEYWORDS = [
    "rally",
    "growth",
    "record high",
    "bullish",
    "upgrade",
    "surge",
    "soar",
    "jump",
    "gain",
    "gains",
    "rises",
    "rise",
    "rose",
    "profit",
    "profit rises",
    "net profit",
    "revenue growth",
    "outperform",
    "beat",
    "beats",
    "strong",
    "recovery",
    "rebound",
    "positive",
    "higher",
    "expand",
    "expands",
    "improve",
    "improves",
    "improved",
    "boosted",
    "boost",
    "upbeat",
    "robust",
    "healthy",
    "wins",
    "won",
    "advance",
    "advances",
]
_NEGATIVE_KEYWORDS = [
    "crash",
    "loss",
    "losses",
    "bearish",
    "downgrade",
    "plunge",
    "slump",
    "decline",
    "shock",
    "meltdown",
    "wipeout",
    "selloff",
    "sell-off",
    "fraud",
    "probe",
    "raid",
    "default",
    "defaults",
    "bankrupt",
    "bankruptcy",
    "debt crisis",
    "warning",
    "miss",
    "misses",
    "missed",
    "underperform",
    "weak",
    "pressure",
    "tumble",
    "tumbles",
    "sinks",
    "plunges",
]

_STRONG_UP_PHRASES = [
    "hits record high",
    "all-time high",
    "surged",
    "rallied sharply",
    "strong double-digit growth",
    "beats estimates",
    "ahead of estimates",
]

_STRONG_DOWN_PHRASES = [
    "crash",
    "crashed",
    "crashes",
    "plunge",
    "plunged",
    "plunges",
    "tumbles",
    "hits lower circuit",
    "misses estimates",
    "below estimates",
    "tank",
    "tanks",
    "tanked",
    "bloodbath",
]


_COMPANY_SECTORS: dict[str, str] = {
    "reliance": "Energy & Retail",
    "hdfc bank": "Banking",
    "icici bank": "Banking",
    "axis bank": "Banking",
    "sbi": "Banking",
    "state bank of india": "Banking",
    "kotak bank": "Banking",
    "indusind bank": "Banking",
    "bajaj finance": "NBFC",
    "bajaj finserv": "NBFC",
    "tcs": "IT Services",
    "infosys": "IT Services",
    "wipro": "IT Services",
    "hcl tech": "IT Services",
    "tech mahindra": "IT Services",
    "maruti": "Automobile",
    "tata motors": "Automobile",
    "mahindra & mahindra": "Automobile",
    "hero motocorp": "Automobile",
    "tvs motor": "Automobile",
    "airtel": "Telecom",
    "vodafone idea": "Telecom",
    "jio": "Telecom",
    "itc": "FMCG",
    "hindustan unilever": "FMCG",
    "hul": "FMCG",
    "nestle india": "FMCG",
    "asian paints": "Consumer",
    "ultratech cement": "Cement",
    "jsw steel": "Metals",
    "tata steel": "Metals",
    "hindalco": "Metals",
    "coal india": "Metals & Mining",
}


_NEWS_FEED: list[NewsFeedArticle] = [
    NewsFeedArticle(
        id=1,
        title="Reliance shares rally on strong retail growth",
        source="DemoWire",
        summary="Reliance Industries reported double-digit growth in its retail business, "
        "driving a rally in the stock despite volatility in the broader market.",
    ),
    NewsFeedArticle(
        id=2,
        title="HDFC Bank faces short-term margin pressure",
        source="DemoWire",
        summary="HDFC Bank management guided for near-term pressure on net interest margins, "
        "but remains positive on medium-term growth.",
    ),
    NewsFeedArticle(
        id=3,
        title="TCS wins large cloud deal from global retailer",
        source="DemoWire",
        summary="IT major TCS announced a multi-year cloud transformation deal, adding visibility "
        "to its order book and growth pipeline.",
    ),
    NewsFeedArticle(
        id=4,
        title="Infosys gains after upbeat FY26 revenue guidance",
        source="DemoWire",
        summary="Infosys shares move higher as the company reiterates double-digit revenue growth "
        "guidance driven by large deal wins in cloud and digital.",
    ),
    NewsFeedArticle(
        id=5,
        title="ICICI Bank reports steady asset quality, NIMs stable",
        source="DemoWire",
        summary="ICICI Bank posts healthy loan growth with stable net interest margins and lower "
        "slippages, supporting a positive outlook for earnings.",
    ),
    NewsFeedArticle(
        id=6,
        title="Maruti to hike prices amid strong SUV demand",
        source="DemoWire",
        summary="Maruti Suzuki announces selective price increases as strong demand in the SUV "
        "segment helps offset input cost pressures.",
    ),
    NewsFeedArticle(
        id=7,
        title="Tata Motors turns net profit on JLR strength",
        source="DemoWire",
        summary="Tata Motors swings to profit as Jaguar Land Rover volumes improve and margin "
        "profile continues to expand.",
    ),
    NewsFeedArticle(
        id=8,
        title="Bharti Airtel adds subscribers, ARPU improves",
        source="DemoWire",
        summary="Telecom major Bharti Airtel reports another quarter of ARPU improvement helped by "
        "premium subscriber additions and tariff mix.",
    ),
    NewsFeedArticle(
        id=9,
        title="HCL Tech signs multi-year cloud migration deal",
        source="DemoWire",
        summary="HCL Tech announces a large multi-year deal to migrate a global bank's core "
        "applications to the cloud, boosting its order book.",
    ),
    NewsFeedArticle(
        id=10,
        title="Wipro focuses on cost optimisation amid muted demand",
        source="DemoWire",
        summary="Wipro highlights cost optimisation and productivity initiatives as it navigates a "
        "muted demand environment in key verticals.",
    ),
]


RSS_FEED_URL = "https://economictimes.indiatimes.com/markets/stocks/rssfeeds/2146842.cms"


_NLP: Optional["Language"] = None


def _get_nlp() -> Optional["Language"]:
    """Lazily load spaCy NER model if available.

    Expects `en_core_web_sm` to be installed. If spaCy or the model is
    missing, returns ``None`` so callers can fall back to heuristics.
    """

    global _NLP
    if _NLP is not None or spacy is None:
        return _NLP

    try:
        _NLP = spacy.load("en_core_web_sm")
    except Exception:
        _NLP = None
    return _NLP


async def _fetch_indian_business_news() -> list[NewsFeedArticle]:
    """Fetch Indian business news from a public RSS feed.

    If anything goes wrong (network/XML issues), we fall back to the
    small in-memory demo feed so the app still works.
    """

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(RSS_FEED_URL)
        resp.raise_for_status()
        text = resp.text
        root = ET.fromstring(text)

        # Typical RSS structure: <rss><channel><item>...</item></channel></rss>
        channel = root.find("channel") or root
        items = channel.findall("item")

        articles: list[NewsFeedArticle] = []
        for idx, item in enumerate(items, start=1):
            title_el = item.find("title")
            desc_el = item.find("description")
            link_el = item.find("link")

            title = (title_el.text or "Untitled").strip() if title_el is not None else "Untitled"
            summary = (desc_el.text or "").strip() if desc_el is not None else ""
            url = (link_el.text or "").strip() if link_el is not None else None

            image_url: str | None = None
            # Many RSS feeds embed an <img> tag inside the description HTML; try to grab its src.
            if summary:
                lower = summary.lower()
                src_idx = lower.find("src=")
                if src_idx != -1:
                    quote_start = summary.find('"', src_idx)
                    if quote_start != -1:
                        quote_end = summary.find('"', quote_start + 1)
                        if quote_end != -1:
                            image_url = summary[quote_start + 1 : quote_end]

            articles.append(
                NewsFeedArticle(
                    id=idx,
                    title=title,
                    source="Economic Times (RSS)",
                    summary=summary,
                    url=url,
                    image_url=image_url,
                )
            )

        if articles:
            return articles
    except Exception:
        # Any RSS/network/XML parsing issue – fall back to demo feed.
        pass

    # If everything failed, return the static demo feed.
    return _NEWS_FEED


async def _fetch_live_headlines(topic: str) -> list[NewsArticle]:
    """Fetch real headlines from the Economic Times RSS feed.

    Filters headlines by topic keyword.  Falls back to generic mock
    headlines only if the RSS fetch fails completely.
    """
    try:
        feed_articles = await _fetch_indian_business_news()
        topic_lower = topic.lower()
        # Filter by topic keyword in title
        matched = [
            NewsArticle(title=a.title, source=a.source, url=a.url)
            for a in feed_articles
            if topic_lower in a.title.lower()
               or any(kw in a.title.lower() for kw in topic_lower.split())
        ]
        # If specific topic didn't match enough, use all headlines
        if len(matched) < 3:
            matched = [
                NewsArticle(title=a.title, source=a.source, url=a.url)
                for a in feed_articles[:15]
            ]
        if matched:
            return matched[:15]
    except Exception:
        pass
    # Ultimate fallback
    base = topic.title()
    return [
        NewsArticle(title=f"{base} shows strong growth this quarter", source="DemoNews"),
        NewsArticle(title=f"Analysts are mixed on {base} outlook", source="DemoNews"),
        NewsArticle(title=f"Short-term volatility expected in {base} sector", source="DemoNews"),
    ]


def _analyze_sentiment_from_titles(titles: list[str]) -> NewsSentimentBreakdown:
    """Heuristic, keyword-based sentiment classifier for headlines.

    Fallback when FinBERT is not available.  Uses word-boundary
    matching and checks positive keywords first to avoid negative
    bias from overly broad negative words.
    """

    pos = neg = neu = 0
    for t in titles:
        lower = _clean_article_text(unescape(t)).lower()
        has_pos = any(re.search(rf"\b{re.escape(k)}\b", lower) for k in _POSITIVE_KEYWORDS)
        has_neg = any(re.search(rf"\b{re.escape(k)}\b", lower) for k in _NEGATIVE_KEYWORDS)
        if has_pos and not has_neg:
            pos += 1
        elif has_neg and not has_pos:
            neg += 1
        elif has_pos and has_neg:
            # Both present — count hits and pick the dominant side
            p_count = sum(1 for k in _POSITIVE_KEYWORDS if re.search(rf"\b{re.escape(k)}\b", lower))
            n_count = sum(1 for k in _NEGATIVE_KEYWORDS if re.search(rf"\b{re.escape(k)}\b", lower))
            if p_count >= n_count:
                pos += 1
            else:
                neg += 1
        else:
            neu += 1
    return NewsSentimentBreakdown(positive=pos, negative=neg, neutral=neu)


def _overall_label(breakdown: NewsSentimentBreakdown) -> str:
    if breakdown.positive > breakdown.negative:
        return "positive"
    if breakdown.negative > breakdown.positive:
        return "negative"
    return "neutral"


def _score_sentiment(text: str) -> tuple[int, int, int, int, int]:
    """Compute a simple keyword-based sentiment score as a FALLBACK.

    Only used when the NLP pipeline is unavailable.
    Uses word-boundary matching to avoid false positives (e.g. 'down'
    matching inside 'download', 'fall' inside 'install').
    Returns (score, pos_hits, neg_hits, strong_up_hits, strong_down_hits).
    """

    # Clean the text: remove boilerplate first
    lower = _clean_article_text(text).lower()
    score = 0
    strong_up_hits = 0
    strong_down_hits = 0
    pos_hits = 0
    neg_hits = 0

    for phrase in _STRONG_UP_PHRASES:
        count = len(re.findall(re.escape(phrase), lower))
        if count > 0:
            strong_up_hits += count
            score += 3 * count

    for phrase in _STRONG_DOWN_PHRASES:
        count = len(re.findall(re.escape(phrase), lower))
        if count > 0:
            strong_down_hits += count
            score -= 3 * count

    # Use word-boundary matching for single keywords
    for kw in _POSITIVE_KEYWORDS:
        pattern = rf"\b{re.escape(kw)}\b"
        count = len(re.findall(pattern, lower))
        if count > 0:
            pos_hits += count
            score += 1 * count

    for kw in _NEGATIVE_KEYWORDS:
        pattern = rf"\b{re.escape(kw)}\b"
        count = len(re.findall(pattern, lower))
        if count > 0:
            neg_hits += count
            score -= 1 * count

    return score, pos_hits, neg_hits, strong_up_hits, strong_down_hits


def _nlp_sentiment_score(
    title: str,
    summary: str,
    full_text: str | None = None,
) -> tuple[float, str, float, dict[str, float] | None, bool]:
    """Primary NLP-based sentiment scoring using FinBERT.

    Analyses the headline (title) and body (summary/full_text)
    SEPARATELY and combines them with proper weighting:
      - Headline sentiment (weight 0.4) – captures the editorial framing
      - Body sentiment   (weight 0.6) – captures nuance and detail

    Returns:
        (nlp_score, nlp_label, nlp_confidence, probs_dict, nlp_used)

    ``nlp_score`` is on a continuous scale [-10, +10].
    ``nlp_label`` is "positive", "negative", or "neutral".
    ``probs_dict`` contains {"positive": p, "negative": n, "neutral": u}.
    ``nlp_used`` is True if FinBERT was actually used.
    """

    # Texts to analyse: always the title, plus the body
    body = full_text if full_text else summary
    texts = [title, body]

    detailed = finbert_sentiment_detailed(texts)
    if detailed is None or len(detailed) < 2:
        return 0.0, "neutral", 0.0, None, False

    title_probs = detailed[0]   # {"positive": p, "negative": n, "neutral": u}
    body_probs = detailed[1]

    # Weighted combination: headline 40%, body 60%
    combined = {
        "positive": 0.4 * title_probs["positive"] + 0.6 * body_probs["positive"],
        "negative": 0.4 * title_probs["negative"] + 0.6 * body_probs["negative"],
        "neutral":  0.4 * title_probs["neutral"]  + 0.6 * body_probs["neutral"],
    }

    # Determine dominant label
    nlp_label = max(combined, key=combined.get)  # type: ignore
    nlp_confidence = combined[nlp_label]

    # Convert to a [-10, +10] score for downstream compatibility
    # score = 10 * (positive_prob - negative_prob)
    nlp_score = 10.0 * (combined["positive"] - combined["negative"])

    return nlp_score, nlp_label, nlp_confidence, combined, True


def _trend_and_recommendation_from_score(score: float) -> tuple[str, str, str]:
    """Map sentiment score to trend, recommendation and explanation.

    Works with both the NLP continuous scale [-10, +10] and the legacy
    keyword integer scale.  Thresholds are calibrated for NLP scores.
    """

    if score >= 3.0:
        trend = "strong_uptrend"
        recommendation = "consider_buy_or_add"
        reasoning = (
            "NLP sentiment analysis indicates a strongly positive tone. "
            "This points to a strong uptrend; suitable for considering fresh "
            "or additional exposure if it fits your risk profile."
        )
    elif score >= 1.0:
        trend = "uptrend"
        recommendation = "consider_buy"
        reasoning = (
            "NLP sentiment analysis indicates a moderately positive tone. "
            "A gradual uptrend is likely; you may consider buying or adding in phases."
        )
    elif score <= -3.0:
        trend = "strong_downtrend"
        recommendation = "avoid_or_reduce_aggressively"
        reasoning = (
            "NLP sentiment analysis indicates a strongly negative tone. "
            "This suggests a strong downtrend; high caution, avoid fresh "
            "positions and consider reducing exposure."
        )
    elif score <= -1.0:
        trend = "downtrend"
        recommendation = "avoid_or_reduce"
        reasoning = (
            "NLP sentiment analysis indicates a moderately negative tone. "
            "Downside risks dominate; avoid new positions or reduce exposure if needed."
        )
    else:
        trend = "sideways"
        recommendation = "hold_watch"
        reasoning = (
            "NLP sentiment analysis shows a balanced or neutral tone "
            "with no clear bullish or bearish bias. Best treated as "
            "'hold and watch' without aggressive action."
        )

    return trend, recommendation, reasoning


def _extract_percentage_move(text: str) -> float | None:
    """Extract the largest percentage move mentioned in the text.

    Looks for patterns like ``up 3%``, ``3% lower``, ``fell 2.5%`` etc. and
    returns a signed value where positive = up, negative = down.
    """

    lower = text.lower()
    candidates: list[float] = []

    up_tokens = r"(up|higher|gain(?:ed)?|gains?|rise|rises|rose|surged?|jump(?:ed)?|jumps?|rall(?:y|ied)|climb(?:ed)?|climbs?|advance(?:d)?|advances?)"
    down_tokens = r"(down|lower|decline(?:d|s)?|fall(?:en|s)?|fell|drop(?:ped|s)?|slid|slides?|plunged?|plunges?|tumbled?|tumbles?|slumped?|crashed?)"

    # e.g. "up 3%", "fell 2.5%"
    pattern_up_prefix = re.compile(rf"{up_tokens}\\s+(\\d+(?:\\.\\d+)?)\\s*%", re.IGNORECASE)
    pattern_down_prefix = re.compile(rf"{down_tokens}\\s+(\\d+(?:\\.\\d+)?)\\s*%", re.IGNORECASE)

    for m in pattern_up_prefix.finditer(lower):
        try:
            value = float(m.group(2))
            candidates.append(value)
        except ValueError:
            continue

    for m in pattern_down_prefix.finditer(lower):
        try:
            value = -float(m.group(2))
            candidates.append(value)
        except ValueError:
            continue

    # e.g. "3% up", "2.5% lower"
    pattern_up_suffix = re.compile(rf"(\\d+(?:\\.\\d+)?)\\s*%\\s+{up_tokens}", re.IGNORECASE)
    pattern_down_suffix = re.compile(rf"(\\d+(?:\\.\\d+)?)\\s*%\\s+{down_tokens}", re.IGNORECASE)

    for m in pattern_up_suffix.finditer(lower):
        try:
            value = float(m.group(1))
            candidates.append(value)
        except ValueError:
            continue

    for m in pattern_down_suffix.finditer(lower):
        try:
            value = -float(m.group(1))
            candidates.append(value)
        except ValueError:
            continue

    if not candidates:
        return None

    # Return the move with the largest absolute impact
    return max(candidates, key=lambda v: abs(v))


def _extract_points_move(text: str) -> float | None:
    """Extract the largest points move (Sensex/Nifty/stock) from the text.

    This is a coarse helper for phrases like ``up 300 points`` or
    ``index fell 150 pts``. Returns a signed value where positive means
    up and negative means down.
    """

    lower = text.lower()
    candidates: list[float] = []

    up_tokens = r"(up|higher|gain(?:ed)?|gains?|rise|rises|rose|surged?|jump(?:ed)?|jumps?|rall(?:y|ied)|climb(?:ed)?|climbs?|advance(?:d)?|advances?)"
    down_tokens = r"(down|lower|decline(?:d|s)?|fall(?:en|s)?|fell|drop(?:ped|s)?|slid|slides?|plunged?|plunges?|tumbled?|tumbles?|slumped?|crashed?)"

    pattern_up_points = re.compile(rf"{up_tokens}\\s+(\\d+(?:\\.\\d+)?)\\s*(points?|pts?)", re.IGNORECASE)
    pattern_down_points = re.compile(rf"{down_tokens}\\s+(\\d+(?:\\.\\d+)?)\\s*(points?|pts?)", re.IGNORECASE)

    for m in pattern_up_points.finditer(lower):
        try:
            value = float(m.group(2))
            candidates.append(value)
        except ValueError:
            continue

    for m in pattern_down_points.finditer(lower):
        try:
            value = -float(m.group(2))
            candidates.append(value)
        except ValueError:
            continue

    if not candidates:
        return None

    return max(candidates, key=lambda v: abs(v))


def _guess_sector_from_text(text: str) -> str:
    """Very rough sector guess based on keywords in text."""

    lower = text.lower()
    if "bank" in lower or "nbfc" in lower:
        return "Banking"
    if any(k in lower for k in ["auto", "motor", "suv", "vehicle"]):
        return "Automobile"
    if any(k in lower for k in ["it services", "software", "tech"]):
        return "IT / Technology"
    if any(k in lower for k in ["steel", "metal", "mining"]):
        return "Metals & Mining"
    if any(k in lower for k in ["cement", "infra", "construction"]):
        return "Infrastructure / Cement"
    if any(k in lower for k in ["pharma", "hospital", "diagnostic"]):
        return "Healthcare / Pharma"
    if any(k in lower for k in ["fmcg", "consumer", "retail"]):
        return "Consumer / FMCG"
    return "Unknown"


def _extract_company_with_spacy(text: str) -> str | None:
    """Use spaCy NER to extract an organisation name, if possible.

    Returns the first ORG entity found, or ``None`` if spaCy/model is
    not available or no suitable entity is detected.
    """

    nlp = _get_nlp()
    if nlp is None:
        return None

    try:
        doc = nlp(text)
    except Exception:
        return None

    for ent in doc.ents:
        if ent.label_ == "ORG":
            name = ent.text.strip()
            name_lower = name.lower()
            # Filter out very short, noisy, or clearly non-company tokens
            if len(name) < 3:
                continue
            if any(bad in name_lower for bad in [
                "function",
                "http",
                "script",
                "cookie",
                "analytics",
                "gtm",
                "boomr",
            ]):
                continue
            # Avoid names that are mostly punctuation/code-like
            if re.fullmatch(r"[A-Za-z0-9 .,&'-]{3,}", name):
                return name
    return None


def _strip_html(html: str) -> str:
    """Strip HTML tags and clean article text for NLP analysis."""

    # Remove script and style blocks completely
    text = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", html)
    # Remove remaining tags
    text = re.sub(r"<[^>]+>", " ", text)
    # Unescape HTML entities and normalize whitespace
    text = unescape(text)
    text = " ".join(text.split())
    return text


# Patterns commonly found in Economic Times / financial news boilerplate
# that pollute sentiment analysis with false signals
_BOILERPLATE_PATTERNS = [
    r"should you buy[,\s]+sell[,\s]+or hold[?.]?",
    r"buy[,\s]+sell[,\s]+or hold[?.]?",
    r"(buy|sell|hold) recommendation",
    r"disclaimer[:\s].*?(?=\.|$)",
    r"(read more|also read|related news|trending now|popular stories)",
    r"(subscribe|newsletter|sign up|log in|sign in)",
    r"(cookie|privacy policy|terms of use|copyright)",
    r"(advertisement|promoted content|sponsored)",
    r"follow us on (twitter|facebook|instagram|linkedin)",
    r"share this (article|story|news)",
    r"\d+ (min|mins|minutes?) read",
    r"(know more|click here|tap here)",
    r"(customers served|claims processed|drives protected)",  # ad text
]
_BOILERPLATE_RE = re.compile(
    "|".join(_BOILERPLATE_PATTERNS), re.IGNORECASE
)


def _clean_article_text(text: str) -> str:
    """Remove website boilerplate, ads, and editorial noise.

    This prevents phrases like 'Should you buy, sell or hold?' from
    polluting the sentiment signal.
    """
    # Remove boilerplate phrases
    text = _BOILERPLATE_RE.sub(" ", text)
    # Remove URLs
    text = re.sub(r"https?://\S+", " ", text)
    # Remove excessive punctuation/special chars
    text = re.sub(r"[\|•►▶◀←→↑↓]{2,}", " ", text)
    # Normalize whitespace
    return " ".join(text.split()).strip()


def _fetch_article_full_text(url: str) -> str | None:
    """Fetch and clean full article text from the given URL.

    Uses a short timeout (3s) to avoid blocking the API.
    Cleans HTML and removes boilerplate before returning.
    """
    try:
        resp = httpx.get(url, timeout=3.0, follow_redirects=True)
        resp.raise_for_status()
        raw = _strip_html(resp.text)
        return _clean_article_text(raw)
    except Exception:
        return None


async def _fetch_article_full_text_async(url: str) -> str | None:
    """Async version of article fetching — non-blocking."""
    try:
        async with httpx.AsyncClient(timeout=3.0, follow_redirects=True) as client:
            resp = await client.get(url)
        resp.raise_for_status()
        raw = _strip_html(resp.text)
        return _clean_article_text(raw)
    except Exception:
        return None


def _analyze_article(title: str, summary: str, url: str | None = None, prefetched_text: str | None = None) -> ArticleAnalysisResponse:
    """NLP-first analysis of a single news article.

    **Primary signal**: FinBERT transformer-based sentiment analysis
    on both the headline and body text, weighted and combined.

    **Secondary signals** (minor adjustments only):
    - Explicit numeric moves ("up 3%", "fell 200 points")
    - Company/sector detection via spaCy NER + dictionary

    This replaces the previous keyword-dominated approach which had
    a strong negative bias and frequently misclassified positive news.
    """

    # Clean HTML entities from title (e.g. &amp; -> &)
    title = unescape(title).strip()
    summary = unescape(summary).strip()
    # Remove boilerplate from title too (e.g. "Should you buy, sell or hold?")
    title_clean = _clean_article_text(title)
    summary_clean = _clean_article_text(summary)

    base_text = f"{title_clean} {summary_clean}"

    # ---- Fetch full article if URL is available ----
    full_text: str | None = prefetched_text
    has_full_article = False
    if full_text is None and url:
        full_text = _fetch_article_full_text(url)
    if full_text:
        has_full_article = True

    # ---- Company / Sector detection ----
    company_text_source = base_text
    if full_text:
        company_text_source = base_text + " " + full_text[:2000]

    company_text_lower = company_text_source.lower()
    company = "No specific company (broad news)"
    sector = "Multi-sector / Index"

    ner_company = _extract_company_with_spacy(company_text_source)
    used_ner_company = False
    used_dict_company = False

    if ner_company:
        key = ner_company.lower()
        company = ner_company
        sector = _COMPANY_SECTORS.get(key, _guess_sector_from_text(company_text_source))
        used_ner_company = True

    if company.startswith("No specific company"):
        for name, sector_name in _COMPANY_SECTORS.items():
            if name in company_text_lower:
                company = name.title()
                sector = sector_name
                used_dict_company = True
                break

    # ==================================================================
    # PRIMARY: NLP-based sentiment via FinBERT
    # ==================================================================
    # Use CLEANED title for NLP (no HTML entities, no boilerplate)
    body_for_nlp = full_text[:3000] if full_text else summary_clean
    nlp_score, nlp_label, nlp_confidence, nlp_probs, nlp_used = _nlp_sentiment_score(
        title=title_clean,
        summary=summary_clean,
        full_text=body_for_nlp,
    )

    # ==================================================================
    # FALLBACK: keyword scoring (only if NLP pipeline is unavailable)
    # ==================================================================
    sentiment_text = (full_text or base_text).lower() if full_text else base_text.lower()

    if not nlp_used:
        # NLP not available — use keyword heuristics as primary
        kw_score, pos_hits, neg_hits, strong_up, strong_down = _score_sentiment(sentiment_text)
        final_score = float(kw_score)
    else:
        # NLP is primary — keywords are only a minor adjustment (±1 max)
        kw_score, pos_hits, neg_hits, strong_up, strong_down = _score_sentiment(sentiment_text)
        # Clamp keyword contribution to ±1.0 so it cannot flip the NLP verdict
        kw_adjust = max(-1.0, min(1.0, kw_score * 0.1))
        final_score = nlp_score + kw_adjust

    # ---- Numeric move adjustments (percentage / points) ----
    pct_move = _extract_percentage_move(sentiment_text)
    pts_move = _extract_points_move(sentiment_text)

    numeric_adjust = 0.0
    numeric_reasons: list[str] = []

    if pct_move is not None:
        if pct_move >= 5:
            numeric_adjust += 2.0
        elif pct_move >= 2:
            numeric_adjust += 1.0
        elif pct_move <= -5:
            numeric_adjust -= 2.0
        elif pct_move <= -2:
            numeric_adjust -= 1.0
        numeric_reasons.append(f"the article mentions a move of about {pct_move:.1f}%")

    if pts_move is not None:
        if pts_move >= 300:
            numeric_adjust += 1.5
        elif pts_move >= 100:
            numeric_adjust += 0.5
        elif pts_move <= -300:
            numeric_adjust -= 1.5
        elif pts_move <= -100:
            numeric_adjust -= 0.5
        direction_word = "up" if pts_move > 0 else "down"
        numeric_reasons.append(
            f"index/stock is reported {direction_word} by roughly {int(abs(pts_move))} points"
        )

    final_score += numeric_adjust

    # ---- Derive trend / recommendation ----
    trend, recommendation, reasoning = _trend_and_recommendation_from_score(final_score)

    if numeric_reasons:
        reasoning = (
            reasoning
            + " Additionally, "
            + " and ".join(numeric_reasons)
            + ", which has been factored into this trend view."
        )

    # ---- Impact strength ----
    if (pct_move is not None and abs(pct_move) >= 5) or (pts_move is not None and abs(pts_move) >= 300):
        impact_strength = "high"
    elif (pct_move is not None and abs(pct_move) >= 2) or (pts_move is not None and abs(pts_move) >= 100):
        impact_strength = "medium"
    elif abs(final_score) >= 5.0:
        impact_strength = "high"
    elif abs(final_score) >= 2.0:
        impact_strength = "medium"
    else:
        impact_strength = "low"

    # ==================================================================
    # Confidence estimation
    # ==================================================================
    confidence = 0.3
    factors: list[str] = []

    if has_full_article:
        confidence += 0.15
        factors.append("we analyzed the full article text, not just the headline")

    if used_ner_company:
        confidence += 0.15
        factors.append("spaCy NER detected the company involved")
    elif used_dict_company:
        confidence += 0.10
        factors.append("the company matches a known list of Indian stocks")
    else:
        factors.append("this looks like broad market news without a single clear company focus")

    if numeric_reasons:
        confidence += 0.15
        factors.append("the article reports explicit numeric moves (percent or points)")

    if nlp_used and nlp_probs is not None:
        # NLP was the primary classifier — this is the most reliable signal
        confidence += 0.25
        pos_pct = int(round(nlp_probs["positive"] * 100))
        neg_pct = int(round(nlp_probs["negative"] * 100))
        neu_pct = int(round(nlp_probs["neutral"] * 100))
        factors.append(
            f"FinBERT NLP sentiment analysis classified this as {nlp_label} "
            f"(positive={pos_pct}%, negative={neg_pct}%, neutral={neu_pct}%)"
        )
        # Extra confidence if the NLP signal is very clear
        if nlp_confidence >= 0.8:
            confidence += 0.10
            factors.append("the NLP model is highly confident in its classification")
    else:
        factors.append(
            "FinBERT NLP pipeline was not available; this view relies on "
            "keyword and numeric cues only (less accurate)"
        )

    # Clamp into [0, 1]
    confidence = max(0.0, min(1.0, confidence))

    if confidence >= 0.75:
        confidence_level = "high"
    elif confidence >= 0.5:
        confidence_level = "medium"
    else:
        confidence_level = "low"

    confidence_explanation = (
        f"Confidence is about {int(round(confidence * 100))}% ({confidence_level}). "
        + " ".join(factors)
        + "."
    )

    return ArticleAnalysisResponse(
        company=company,
        sector=sector,
        trend=trend,
        recommendation=recommendation,
        reasoning=reasoning,
        confidence_score=confidence,
        confidence_level=confidence_level,
        confidence_explanation=confidence_explanation,
        impact_strength=impact_strength,
    )


@router.post("/analyze_news", response_model=NewsAnalysisResponse)
async def analyze_news(payload: NewsAnalysisRequest) -> NewsAnalysisResponse:
    """News sentiment and simple trend view for a topic (Agent B).

    The agent tries to use FinBERT for sentiment on sample headlines.
    If the FinBERT model or transformers are unavailable, it falls
    back to the project’s keyword-based baseline.
    """

    articles = await _fetch_live_headlines(payload.topic)
    titles = [a.title for a in articles]

    finbert_results = finbert_sentiment(titles)

    if finbert_results is not None and len(finbert_results) == len(titles):
        # Use FinBERT labels as the primary sentiment signal
        pos = neg = neu = 0
        for label, _conf in finbert_results:
            if label == "positive":
                pos += 1
            elif label == "negative":
                neg += 1
            else:
                neu += 1
        breakdown = NewsSentimentBreakdown(positive=pos, negative=neg, neutral=neu)
    else:
        # Fallback to baseline keyword model
        breakdown = _analyze_sentiment_from_titles(titles)

    overall = _overall_label(breakdown)

    total = breakdown.positive + breakdown.negative + breakdown.neutral or 1
    features = NewsSentimentFeatures(
        positive_ratio=breakdown.positive / total,
        negative_ratio=breakdown.negative / total,
        neutral_ratio=breakdown.neutral / total,
        net_score=(breakdown.positive - breakdown.negative) / total,
    )

    trend_label: str | None = None
    trend_conf: float | None = None
    trend_result = predict_trend(features)
    if trend_result is not None:
        trend_label, trend_conf = trend_result

    # Rough sector hint from the topic + titles (heuristic mapping).
    # This reuses the same kind of keyword-based rules as the
    # single-article analysis so the app can talk about sectors.
    sector_counts: dict[str, int] = {}
    for art in articles:
        sector = _guess_sector_from_text(f"{payload.topic} {art.title}")
        sector_counts[sector] = sector_counts.get(sector, 0) + 1
    top_sector = max(sector_counts, key=sector_counts.get) if sector_counts else "Unknown"

    summary_parts = [
        f"Analyzed {len(articles)} live headlines about '{payload.topic}'. ",
        f"Overall sentiment: {overall.upper()} ",
        f"({breakdown.positive} positive, {breakdown.negative} negative, {breakdown.neutral} neutral).",
        f" Dominant sector: {top_sector}.",
    ]

    if trend_label is not None and trend_conf is not None:
        summary_parts.append(
            f" A small ML model that maps aggregate sentiment to a short-term view ")
        summary_parts.append(
            f"sees this setup as {trend_label.upper()} (≈ {trend_conf * 100:.0f}% confidence)."
        )
    else:
        summary_parts.append(
            " Treat this as a qualitative sentiment snapshot rather than a precise signal."
        )

    summary = "".join(summary_parts)

    # ---- LLM-powered market narrative (Tier 2: Agentic reasoning) ----
    llm_summary: str | None = None
    try:
        groq = get_groq_client()
        system_prompt = (
            "You are Agent B, a financial news analyst AI focused on Indian markets. "
            "You receive structured NLP analysis (FinBERT sentiment, trend ML model, "
            "sector classification) computed by traditional ML pipelines. Your job is "
            "to synthesize this data into a concise, insightful market narrative. "
            "Be specific about the sentiment data. Keep it to 4-6 sentences. "
            "Mention sectors, trends, and confidence levels."
        )
        headlines_text = "\n".join(
            f"- {a.title} [{a.source}]" for a in articles[:8]
        )
        user_prompt = (
            f"Topic: {payload.topic}\n"
            f"Headlines analyzed:\n{headlines_text}\n\n"
            f"Sentiment breakdown: {breakdown.positive} positive, "
            f"{breakdown.negative} negative, {breakdown.neutral} neutral\n"
            f"Overall sentiment: {overall}\n"
            f"ML trend prediction: {trend_label or 'N/A'} "
            f"(confidence: {trend_conf * 100:.0f}%)\n" if trend_conf else
            f"ML trend prediction: unavailable\n"
            f"Dominant sector: {top_sector}\n"
            f"\nWrite a market narrative synthesizing these signals."
        )
        llm_summary = await groq.chat(system_prompt, user_prompt, max_tokens=400)
    except Exception as exc:
        print(f"[Agent B] LLM summary generation failed: {exc}")

    return NewsAnalysisResponse(
        topic=payload.topic,
        overall_sentiment=overall,
        sentiment_breakdown=breakdown,
        sample_articles=articles,
        summary=summary,
        trend_label=trend_label,
        trend_confidence=trend_conf,
        dominant_sector=top_sector,
        llm_summary=llm_summary,
    )


# ── Live market index data ──

class MarketIndexResponse(BaseModel):
    """Live market index snapshot."""
    name: str
    price: float
    change: float
    change_percent: float
    source: str
    timestamp: str | None = None


# ── NSE cache (shared across market_index + sector_indices) ──
_nse_cache: dict = {"data": None, "ts": 0.0}
_NSE_CACHE_TTL = 60  # seconds


async def _fetch_nse_all_indices() -> list[dict] | None:
    """Fetch all indices from NSE India API with 60s in-memory cache."""
    import time
    now = time.time()
    if _nse_cache["data"] is not None and (now - _nse_cache["ts"]) < _NSE_CACHE_TTL:
        return _nse_cache["data"]

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "application/json",
        "Referer": "https://www.nseindia.com/",
    }
    try:
        async with httpx.AsyncClient(timeout=8.0, follow_redirects=True) as client:
            await client.get("https://www.nseindia.com", headers=headers)
            resp = await client.get("https://www.nseindia.com/api/allIndices", headers=headers)
            if resp.status_code == 200:
                data = resp.json().get("data", [])
                _nse_cache["data"] = data
                _nse_cache["ts"] = now
                return data
    except Exception as exc:
        print(f"[NSE] API error: {exc}")
    # Return stale cache if fetch failed
    return _nse_cache["data"]


async def _fetch_yahoo_fallback() -> list[MarketIndexResponse]:
    """Fallback: compute change from Yahoo Finance v8 5-day chart."""
    import datetime
    symbols = [
        ("NIFTY 50", "^NSEI"),
        ("BSE SENSEX", "^BSESN"),
    ]
    results: list[MarketIndexResponse] = []
    try:
        _YF_HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
        async with httpx.AsyncClient(timeout=8.0, headers=_YF_HEADERS) as client:
            for name, sym in symbols:
                try:
                    url = f"https://query1.finance.yahoo.com/v8/finance/chart/{sym}?range=5d&interval=1d"
                    resp = await client.get(url)
                    if resp.status_code == 200:
                        data = resp.json()
                        meta = data["chart"]["result"][0]["meta"]
                        closes = data["chart"]["result"][0]["indicators"]["quote"][0]["close"]
                        closes = [c for c in closes if c is not None]
                        price = meta.get("regularMarketPrice", closes[-1] if closes else 0)
                        prev = closes[-2] if len(closes) >= 2 else price
                        change = round(price - prev, 2)
                        pct = round((change / prev) * 100, 2) if prev else 0.0
                        results.append(MarketIndexResponse(
                            name=name, price=round(price, 2), change=change,
                            change_percent=pct, source="Yahoo Finance",
                            timestamp=datetime.datetime.now().isoformat(),
                        ))
                except Exception as exc:
                    print(f"[Yahoo fallback] {name}: {exc}")
    except Exception as exc:
        print(f"[Yahoo] HTTP error: {exc}")
    return results


class SectorIndexResponse(BaseModel):
    """Sector index snapshot."""
    name: str
    display_name: str
    price: float
    change: float
    change_percent: float


class MarketDataResponse(BaseModel):
    """Combined market + sector data in a single call."""
    indices: list[MarketIndexResponse]
    sectors: list[SectorIndexResponse]


_SECTOR_MAP = {
    "NIFTY BANK": "Banking",
    "NIFTY IT": "IT",
    "NIFTY PHARMA": "Pharma",
    "NIFTY AUTO": "Auto",
    "NIFTY ENERGY": "Energy",
    "NIFTY FMCG": "FMCG",
    "NIFTY METAL": "Metals",
    "NIFTY REALTY": "Realty",
    "NIFTY FINANCIAL SERVICES": "Finance",
}
_WANTED_INDICES = {"NIFTY 50"}

# BSE SENSEX cache (60s TTL, same as NSE)
_sensex_cache: dict = {"data": None, "ts": 0.0}

async def _fetch_sensex() -> MarketIndexResponse | None:
    """Fetch BSE SENSEX from Yahoo Finance (cached 60s)."""
    import datetime, time as _time
    now = _time.time()
    if _sensex_cache["data"] and now - _sensex_cache["ts"] < 60:
        return _sensex_cache["data"]
    try:
        _YF_HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
        async with httpx.AsyncClient(timeout=8.0, headers=_YF_HEADERS) as client:
            url = "https://query1.finance.yahoo.com/v8/finance/chart/%5EBSESN?range=5d&interval=1d"
            resp = await client.get(url)
            if resp.status_code == 200:
                data = resp.json()
                meta = data["chart"]["result"][0]["meta"]
                closes = data["chart"]["result"][0]["indicators"]["quote"][0]["close"]
                closes = [c for c in closes if c is not None]
                price = meta.get("regularMarketPrice", closes[-1] if closes else 0)
                prev = closes[-2] if len(closes) >= 2 else price
                change = round(price - prev, 2)
                pct = round((change / prev) * 100, 2) if prev else 0.0
                result = MarketIndexResponse(
                    name="BSE SENSEX", price=round(price, 2), change=change,
                    change_percent=pct, source="Yahoo Finance",
                    timestamp=datetime.datetime.now().isoformat(),
                )
                _sensex_cache["data"] = result
                _sensex_cache["ts"] = now
                return result
    except Exception as exc:
        print(f"[SENSEX] fetch error: {exc}")
    return _sensex_cache.get("data")


@router.get("/market_data", response_model=MarketDataResponse)
async def market_data() -> MarketDataResponse:
    """Single endpoint returning both market indices AND sector data (1 NSE fetch)."""
    import datetime
    nse_data = await _fetch_nse_all_indices()
    indices: list[MarketIndexResponse] = []
    sectors: list[SectorIndexResponse] = []
    if nse_data:
        ts = datetime.datetime.now().isoformat()
        for item in nse_data:
            idx_name = item.get("index", "")
            if idx_name in _WANTED_INDICES:
                indices.append(MarketIndexResponse(
                    name=idx_name,
                    price=float(item.get("last", 0)),
                    change=float(item.get("variation", 0)),
                    change_percent=float(item.get("percentChange", 0)),
                    source="NSE India",
                    timestamp=ts,
                ))
            if idx_name in _SECTOR_MAP:
                sectors.append(SectorIndexResponse(
                    name=idx_name,
                    display_name=_SECTOR_MAP[idx_name],
                    price=float(item.get("last", 0)),
                    change=float(item.get("variation", 0)),
                    change_percent=float(item.get("percentChange", 0)),
                ))
    if not indices:
        indices = await _fetch_yahoo_fallback()
    else:
        # Always add BSE SENSEX from Yahoo (not available on NSE endpoint)
        sensex = await _fetch_sensex()
        if sensex:
            indices.append(sensex)
    return MarketDataResponse(indices=indices, sectors=sectors)


# Keep legacy endpoints (they now use the same cache, so they're fast)
@router.get("/market_index", response_model=list[MarketIndexResponse])
async def market_index() -> list[MarketIndexResponse]:
    """Legacy: returns just market indices."""
    data = await market_data()
    return data.indices


@router.get("/sector_indices", response_model=list[SectorIndexResponse])
async def sector_indices() -> list[SectorIndexResponse]:
    """Legacy: returns just sector indices."""
    data = await market_data()
    return data.sectors


@router.get("/news_feed", response_model=list[NewsFeedArticle])
async def news_feed() -> list[NewsFeedArticle]:
    """Return latest Indian business headlines for the app.

    If a ``NEWSAPI_KEY`` environment variable is configured, this will
    fetch live top business headlines for India via NewsAPI
    (``country=in, category=business``). Otherwise it falls back to a
    small in-memory demo feed so that the app still works.
    """

    return await _fetch_indian_business_news()


@router.post("/analyze_article", response_model=ArticleAnalysisResponse)
async def analyze_article(payload: ArticleAnalysisRequest) -> ArticleAnalysisResponse:
    """Analyze a specific news item and produce a simple investment view.

    Uses async HTTP fetch for the full article to avoid timeouts.
    LLM generates deep reasoning on top of NLP analysis.
    """

    # Pre-fetch article text asynchronously to avoid blocking
    prefetched: str | None = None
    if payload.url:
        prefetched = await _fetch_article_full_text_async(payload.url)

    result = _analyze_article(
        title=payload.title,
        summary=payload.summary,
        url=payload.url,
        prefetched_text=prefetched,
    )

    # ---- LLM-powered article reasoning (Tier 2) ----
    llm_reasoning: str | None = None
    try:
        groq = get_groq_client()
        system_prompt = (
            "You are Agent B, a financial news analyst AI. You receive structured "
            "NLP analysis of a specific news article (company, sector, trend, "
            "confidence from FinBERT + spaCy). Your job is to provide deeper "
            "reasoning about the article's potential market impact. "
            "Keep it to 3-4 sentences. Be specific and analytical."
        )
        user_prompt = (
            f"Article: {payload.title}\n"
            f"Summary: {payload.summary}\n\n"
            f"NLP Analysis Results:\n"
            f"- Company: {result.company}\n"
            f"- Sector: {result.sector}\n"
            f"- Trend: {result.trend}\n"
            f"- Impact: {result.impact_strength}\n"
            f"- Confidence: {result.confidence_score:.0%} ({result.confidence_level})\n"
            f"- Rule-based reasoning: {result.reasoning}\n"
            f"\nProvide a deeper analytical reasoning about this article's market impact."
        )
        llm_reasoning = await groq.chat(system_prompt, user_prompt, max_tokens=300)
    except Exception as exc:
        print(f"[Agent B] LLM article reasoning failed: {exc}")

    return ArticleAnalysisResponse(
        company=result.company,
        sector=result.sector,
        trend=result.trend,
        recommendation=result.recommendation,
        reasoning=result.reasoning,
        confidence_score=result.confidence_score,
        confidence_level=result.confidence_level,
        confidence_explanation=result.confidence_explanation,
        impact_strength=result.impact_strength,
        llm_reasoning=llm_reasoning,
    )
