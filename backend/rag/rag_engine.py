"""RAG knowledge retrieval engine for Agent C.

Provides a curated financial knowledge corpus with TF-IDF scoring
for relevant chunk retrieval. Used by the /synthesize endpoint
to ground LLM responses in factual financial best practices.
"""

import math
import re
from collections import Counter
from dataclasses import dataclass
from typing import Dict, List


@dataclass
class KnowledgeChunk:
    id: int
    title: str
    content: str
    tags: List[str]


# ---- Curated Financial Knowledge Corpus ----

_CORPUS: List[KnowledgeChunk] = [
    # Emergency Fund & Safety Net
    KnowledgeChunk(
        id=1,
        title="Emergency Fund Basics",
        content=(
            "An emergency fund should cover 3-6 months of essential living expenses. "
            "Keep it in a liquid instrument like a savings account or liquid mutual fund. "
            "This protects you from sudden job loss, medical emergencies, or unexpected repairs "
            "without having to sell investments at a loss or take on debt."
        ),
        tags=["emergency", "fund", "safety", "savings", "liquid"],
    ),
    KnowledgeChunk(
        id=2,
        title="Why Emergency Funds Come First",
        content=(
            "Before investing in stocks, mutual funds, or any market-linked product, "
            "ensure your emergency fund is fully funded. Without this safety net, "
            "a single financial shock can force you to liquidate investments at the worst time, "
            "locking in losses and derailing your financial plan."
        ),
        tags=["emergency", "investing", "priority", "safety"],
    ),
    # Budgeting
    KnowledgeChunk(
        id=3,
        title="50-30-20 Budgeting Rule",
        content=(
            "The 50-30-20 rule divides after-tax income into: 50%% needs (rent, groceries, EMIs), "
            "30%% wants (dining out, entertainment, shopping), 20%% savings and investments. "
            "This simple framework ensures you live within your means while building wealth. "
            "Adjust the ratios based on your income level and goals."
        ),
        tags=["budget", "spending", "rule", "saving", "income"],
    ),
    KnowledgeChunk(
        id=4,
        title="Tracking Expenses",
        content=(
            "Track every expense for at least one month to understand where your money goes. "
            "Use categories like food, transport, rent, subscriptions, and entertainment. "
            "Most people discover 10-15%% of their spending is on things they don't value. "
            "Eliminating these 'money leaks' can fund your investment goals."
        ),
        tags=["expense", "tracking", "budget", "spending", "category"],
    ),
    KnowledgeChunk(
        id=5,
        title="Cutting Discretionary Spending",
        content=(
            "To reduce expenses without feeling deprived, use the 'value per rupee' test: "
            "for each discretionary expense, ask if it brings proportional happiness. "
            "Common high-impact cuts: cook at home 4 days/week (saves \u20b94000-8000/month), "
            "review and cancel unused subscriptions, use public transport for short commutes."
        ),
        tags=["expense", "cut", "dining", "saving", "spending"],
    ),
    # Debt Management
    KnowledgeChunk(
        id=6,
        title="Debt Prioritization: Avalanche Method",
        content=(
            "The avalanche method: pay minimum on all debts, then throw every extra rupee "
            "at the highest-interest debt first. Credit cards (30-40%% APR) > personal loans (12-18%%) "
            "> education loans (8-10%%) > home loans (7-9%%). This minimizes total interest paid "
            "and is mathematically optimal."
        ),
        tags=["debt", "loan", "credit", "interest", "emi", "avalanche"],
    ),
    KnowledgeChunk(
        id=7,
        title="Debt Prioritization: Snowball Method",
        content=(
            "The snowball method: pay off debts from smallest balance to largest, regardless of interest rate. "
            "This provides quick psychological wins that keep you motivated. "
            "While mathematically inferior to the avalanche method, behavioural research shows "
            "people using snowball are more likely to become debt-free."
        ),
        tags=["debt", "loan", "snowball", "motivation"],
    ),
    KnowledgeChunk(
        id=8,
        title="Credit Card Debt Trap",
        content=(
            "Credit card interest rates in India range from 30-42%% per annum. "
            "Paying only the minimum due means a \u20b950,000 balance can take 8+ years to clear "
            "and cost \u20b91,50,000+ in interest. Always pay the full statement balance. "
            "If you can't, consider a balance transfer to a lower-rate personal loan."
        ),
        tags=["credit", "card", "debt", "interest", "trap"],
    ),
    # Investing Basics
    KnowledgeChunk(
        id=9,
        title="SIP Investing for Beginners",
        content=(
            "A Systematic Investment Plan (SIP) invests a fixed amount monthly into mutual funds. "
            "Benefits: rupee cost averaging (buy more units when markets are low), "
            "discipline through automation, and no need to time the market. "
            "Start with \u20b9500-5000/month in an index fund like Nifty 50 or Sensex."
        ),
        tags=["sip", "invest", "mutual", "fund", "beginner", "index"],
    ),
    KnowledgeChunk(
        id=10,
        title="Diversification Across Asset Classes",
        content=(
            "Diversify across equity (stocks/mutual funds), debt (FDs, bonds, debt funds), "
            "gold (sovereign gold bonds, gold ETFs), and real estate. "
            "A common allocation for moderate risk: 50-60%% equity, 20-30%% debt, 10-15%% gold. "
            "Rebalance annually to maintain your target allocation."
        ),
        tags=["diversify", "asset", "equity", "debt", "gold", "allocation"],
    ),
    KnowledgeChunk(
        id=11,
        title="Index Funds vs Active Funds",
        content=(
            "Index funds track a market index (Nifty 50, Sensex) with low expense ratios (0.1-0.5%%). "
            "Over 10+ years, 80%% of active fund managers fail to beat the index after fees. "
            "For most investors, a simple Nifty 50 index fund or Nifty Next 50 fund "
            "provides better risk-adjusted returns than actively managed funds."
        ),
        tags=["index", "fund", "active", "passive", "nifty", "expense"],
    ),
    KnowledgeChunk(
        id=12,
        title="Power of Compounding",
        content=(
            "\u20b95,000/month invested at 12%% annual returns grows to \u20b91.76 crore in 30 years. "
            "Starting 5 years earlier adds \u20b91.5 crore more. Starting 10 years late means "
            "you'd need \u20b916,000/month to reach the same goal. "
            "The single most important factor in wealth building is time in the market."
        ),
        tags=["compound", "invest", "time", "growth", "long", "term"],
    ),
    # Risk Management
    KnowledgeChunk(
        id=13,
        title="Risk Tolerance Assessment",
        content=(
            "Your risk tolerance depends on: age (younger = more risk capacity), "
            "income stability (salaried vs freelance), financial obligations (EMIs, dependents), "
            "and investment horizon (>7 years = can handle equity volatility). "
            "A 25-year-old with stable income can allocate 70-80%% to equity; "
            "a 50-year-old near retirement should hold 30-40%% equity maximum."
        ),
        tags=["risk", "tolerance", "age", "aggressive", "conservative"],
    ),
    KnowledgeChunk(
        id=14,
        title="When Markets Crash",
        content=(
            "Market crashes of 20-40%% happen every 7-10 years on average. "
            "Historical data shows: if you stay invested through crashes, "
            "markets have always recovered within 2-4 years. "
            "Selling during a crash locks in losses. Continue SIPs during downturns "
            "to buy units at lower prices — this accelerates long-term wealth creation."
        ),
        tags=["market", "crash", "bear", "correction", "volatility", "stay"],
    ),
    KnowledgeChunk(
        id=15,
        title="Position Sizing",
        content=(
            "Never invest more than 5-10%% of your portfolio in a single stock. "
            "For sectoral funds, limit to 15-20%% of total equity allocation. "
            "This ensures that one bad pick doesn't destroy your portfolio. "
            "The more concentrated your bets, the higher the risk of permanent capital loss."
        ),
        tags=["risk", "position", "stock", "concentration", "portfolio"],
    ),
    # Tax & Insurance
    KnowledgeChunk(
        id=16,
        title="Tax-Saving Investments (Section 80C)",
        content=(
            "Section 80C allows deductions up to \u20b91.5 lakh/year from taxable income. "
            "Best options: ELSS mutual funds (3-year lock-in, equity returns), "
            "PPF (15-year, guaranteed ~7%%, fully tax-free), "
            "NPS (additional \u20b950,000 under 80CCD(1B)). "
            "Avoid insurance-cum-investment plans (ULIPs, endowment) — they offer poor returns."
        ),
        tags=["tax", "80c", "elss", "ppf", "nps", "saving", "deduction"],
    ),
    KnowledgeChunk(
        id=17,
        title="Health Insurance",
        content=(
            "A health insurance cover of \u20b910-20 lakh is essential for every family. "
            "Medical inflation in India is 12-15%% annually. A single hospitalization "
            "can wipe out years of savings. Buy term health insurance early (premiums are "
            "lower when young) and add a super top-up for catastrophic coverage."
        ),
        tags=["insurance", "health", "medical", "cover", "premium"],
    ),
    KnowledgeChunk(
        id=18,
        title="Term Life Insurance",
        content=(
            "Term insurance provides pure life cover at the lowest cost. "
            "Rule of thumb: cover = 10-15x your annual income. "
            "A 25-year-old can get \u20b91 crore cover for just \u20b9600-800/month. "
            "Never mix insurance with investment — buy term insurance and invest separately."
        ),
        tags=["insurance", "term", "life", "cover", "income"],
    ),
    # Indian Market Specific
    KnowledgeChunk(
        id=19,
        title="UPI and Digital Payments Safety",
        content=(
            "Never share your UPI PIN with anyone. Banks and UPI apps will never call "
            "asking for your PIN or OTP. If you receive money from an unknown sender, "
            "do not scan any QR code they send — QR codes are for PAYING, not receiving. "
            "Enable UPI transaction limits and use separate accounts for daily spending."
        ),
        tags=["upi", "payment", "digital", "safety", "fraud", "scam"],
    ),
    KnowledgeChunk(
        id=20,
        title="Fixed Deposits vs Debt Funds",
        content=(
            "FDs offer guaranteed returns (5-7%%) but are fully taxable. "
            "Debt mutual funds offer similar returns with better tax efficiency "
            "(indexation benefit reduces tax on gains held >3 years). "
            "For emergencies, use liquid funds (instant redemption up to \u20b950,000). "
            "For 1-3 year goals, use short-duration debt funds."
        ),
        tags=["fd", "fixed", "deposit", "debt", "fund", "liquid", "tax"],
    ),
    KnowledgeChunk(
        id=21,
        title="Nifty 50 Historical Returns",
        content=(
            "Nifty 50 has delivered approximately 11-13%% CAGR over the last 20 years. "
            "In any single year, returns can range from -50%% to +75%%. "
            "However, over any 10-year rolling period, Nifty has never delivered negative returns. "
            "This demonstrates why equity investing requires a long-term horizon of 7+ years."
        ),
        tags=["nifty", "stock", "market", "return", "equity", "history"],
    ),
    KnowledgeChunk(
        id=22,
        title="Gold as a Portfolio Hedge",
        content=(
            "Gold is negatively correlated with equity during crises. Allocate 10-15%% to gold. "
            "Best options: Sovereign Gold Bonds (2.5%% annual interest + gold appreciation, tax-free at maturity), "
            "Gold ETFs (liquid, no storage hassle), or digital gold platforms. "
            "Avoid physical gold jewellery as investment due to making charges and storage risk."
        ),
        tags=["gold", "sgb", "hedge", "diversify", "allocation"],
    ),
    # Behavioural Finance
    KnowledgeChunk(
        id=23,
        title="Avoiding Emotional Investing",
        content=(
            "The biggest enemy of good returns is your own emotions. "
            "Fear causes selling at market bottoms; greed causes buying at peaks. "
            "Solution: automate through SIPs, set predetermined allocation rules, "
            "and avoid checking portfolio daily. Studies show investors who check less "
            "frequently earn higher returns."
        ),
        tags=["emotion", "behaviour", "fear", "greed", "sip", "discipline"],
    ),
    KnowledgeChunk(
        id=24,
        title="Lifestyle Inflation Trap",
        content=(
            "When income increases, resist the urge to immediately upgrade lifestyle. "
            "The '50%% rule': save/invest at least 50%% of every salary raise. "
            "If your salary goes from \u20b950K to \u20b970K, invest \u20b910K of the \u20b920K increase. "
            "This simple habit is the fastest path to financial independence."
        ),
        tags=["lifestyle", "inflation", "raise", "salary", "spending", "saving"],
    ),
    # Goals & Planning
    KnowledgeChunk(
        id=25,
        title="Goal-Based Investing",
        content=(
            "Match investments to goals by time horizon: "
            "Short-term (1-3 years) \u2192 debt funds, FDs. "
            "Medium-term (3-7 years) \u2192 balanced/hybrid funds. "
            "Long-term (7+ years) \u2192 equity index funds, ELSS. "
            "Never use equity for goals less than 5 years away."
        ),
        tags=["goal", "invest", "horizon", "short", "long", "term", "plan"],
    ),
    KnowledgeChunk(
        id=26,
        title="Retirement Planning in Your 20s",
        content=(
            "Starting at age 25 vs 35: you need to invest 3x less monthly to reach the same corpus at 60. "
            "Target: 25-30x annual expenses as retirement corpus. "
            "Use NPS (low-cost, tax benefits) + equity mutual funds + PPF for a diversified retirement portfolio. "
            "Automate contributions so retirement saving is invisible."
        ),
        tags=["retirement", "pension", "nps", "ppf", "young", "plan"],
    ),
    # Scams & Red Flags
    KnowledgeChunk(
        id=27,
        title="Investment Scam Red Flags",
        content=(
            "Red flags: guaranteed returns above 12%%, pressure to invest NOW, "
            "no SEBI/RBI registration, returns from recruiting new members (Ponzi), "
            "complex structures you don't understand. "
            "Rule: if it sounds too good to be true, it is. "
            "Always verify with SEBI's investor portal before investing."
        ),
        tags=["scam", "fraud", "ponzi", "guaranteed", "return", "red", "flag"],
    ),
    KnowledgeChunk(
        id=28,
        title="Mutual Fund Selection Criteria",
        content=(
            "Choose mutual funds based on: consistent 5+ year track record, "
            "low expense ratio (<1%% for equity, <0.5%% for debt), "
            "fund house reputation and AUM >\u20b95,000 crore, "
            "and the fund manager's tenure (>3 years). "
            "Avoid NFOs (New Fund Offers) — they have no track record."
        ),
        tags=["mutual", "fund", "select", "expense", "ratio", "aum"],
    ),
    # Salary & Income
    KnowledgeChunk(
        id=29,
        title="Salary Structure Optimization",
        content=(
            "Restructure salary for tax savings: maximize HRA (rent receipts), "
            "use LTA for travel, claim standard deduction of \u20b950,000, "
            "route \u20b91.5 lakh into 80C (ELSS/PPF), \u20b925,000 into health insurance (80D), "
            "and \u20b950,000 into NPS (80CCD). This can save \u20b91-2 lakh in taxes annually."
        ),
        tags=["salary", "tax", "hra", "deduction", "80c", "80d", "income"],
    ),
    KnowledgeChunk(
        id=30,
        title="Side Income Ideas for Salaried Workers",
        content=(
            "Build secondary income streams: freelancing (\u20b910-50K/month based on skill), "
            "dividend-paying stocks/funds, rental income from small properties, "
            "online tutoring or course creation, or interest from debt instruments. "
            "Secondary income accelerates financial independence and provides a buffer."
        ),
        tags=["income", "side", "freelance", "dividend", "rental", "passive"],
    ),
    # Real Estate
    KnowledgeChunk(
        id=31,
        title="Renting vs Buying a Home",
        content=(
            "The buy vs rent decision hinges on the price-to-rent ratio. "
            "In Indian metros, buying often costs 200-300x monthly rent. "
            "If EMI + maintenance > 1.5x rent, renting and investing the difference "
            "in equity often builds more wealth. Buy only if you'll stay 7+ years "
            "and the EMI is <40%% of take-home pay."
        ),
        tags=["rent", "buy", "home", "real", "estate", "emi", "property"],
    ),
    KnowledgeChunk(
        id=32,
        title="Home Loan Tips",
        content=(
            "Home loan best practices: keep tenure at 15-20 years (not 30), "
            "make prepayments whenever you receive bonuses (even \u20b950K/year reduces tenure significantly), "
            "compare rates across banks (0.5%% difference = lakhs over loan tenure), "
            "and always take a reducing-balance EMI, never flat-rate."
        ),
        tags=["home", "loan", "emi", "prepay", "interest", "mortgage"],
    ),
]


# ---- TF-IDF Scoring ----

_STOP_WORDS = frozenset({
    "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
    "in", "on", "at", "to", "for", "of", "with", "by", "from", "and",
    "or", "not", "but", "if", "this", "that", "it", "its", "as", "so",
    "do", "does", "did", "will", "would", "can", "could", "should",
    "have", "has", "had", "may", "might", "shall", "must",
    "i", "me", "my", "you", "your", "we", "our", "they", "their",
    "what", "which", "who", "how", "when", "where", "why",
    "very", "more", "most", "than", "also", "just", "about",
})


def _tokenize(text: str) -> List[str]:
    """Lowercase, strip punctuation, remove stop words."""
    tokens = re.findall(r"[a-z0-9]+", text.lower())
    return [t for t in tokens if t not in _STOP_WORDS and len(t) > 1]


def _build_idf() -> Dict[str, float]:
    """Compute IDF (inverse document frequency) over the corpus."""
    n = len(_CORPUS)
    doc_freq: Counter = Counter()
    for chunk in _CORPUS:
        unique_tokens = set(_tokenize(chunk.content + " " + chunk.title + " " + " ".join(chunk.tags)))
        for token in unique_tokens:
            doc_freq[token] += 1
    return {
        token: math.log((n + 1) / (freq + 1)) + 1  # smoothed IDF
        for token, freq in doc_freq.items()
    }


# Precompute IDF at import time
_IDF = _build_idf()


def _tfidf_score(query: str, chunk: KnowledgeChunk) -> float:
    """TF-IDF based relevance score between query and a knowledge chunk."""
    query_tokens = _tokenize(query)
    if not query_tokens:
        return 0.0

    # Query term frequencies
    query_tf = Counter(query_tokens)

    # Chunk tokens (content + title + tags for better matching)
    chunk_text = chunk.content + " " + chunk.title + " " + " ".join(chunk.tags)
    chunk_tokens = _tokenize(chunk_text)
    chunk_tf = Counter(chunk_tokens)

    # Compute dot product of TF-IDF vectors
    score = 0.0
    for token, q_count in query_tf.items():
        if token in chunk_tf:
            idf = _IDF.get(token, 1.0)
            # TF-IDF for query and chunk, weighted by IDF
            score += (q_count * idf) * (chunk_tf[token] * idf)

    # Bonus for tag matches (tags are curated keywords, so exact match is very relevant)
    query_set = set(query_tokens)
    tag_matches = sum(1 for tag in chunk.tags if tag in query_set)
    score += tag_matches * 3.0  # strong bonus for tag hits

    return score


def retrieve(query: str, k: int = 3) -> List[KnowledgeChunk]:
    """Retrieve top-k relevant knowledge chunks using TF-IDF scoring.

    Returns the most relevant chunks from the financial knowledge corpus.
    Used by Agent C to ground LLM responses in factual best practices.
    """
    scored = [(_tfidf_score(query, c), c) for c in _CORPUS]
    scored.sort(key=lambda x: x[0], reverse=True)

    # Filter out zero-score chunks
    filtered = [(s, c) for s, c in scored if s > 0]

    if filtered:
        return [c for _, c in filtered[:k]]

    # If nothing matched (very generic query), return top chunks by ID
    return _CORPUS[:k]
