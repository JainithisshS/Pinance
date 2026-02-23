from typing import Literal, Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from ..rag.rag_engine import retrieve
from ..services.groq_client import get_groq_client


router = APIRouter()


class ChatTurn(BaseModel):
    """Single chat turn used by the Agent C chatbot interface."""

    role: Literal["user", "assistant"]
    content: str


class RecommendationRequest(BaseModel):
    """Context passed into Agent C.

    In a full app, ``finance_insight`` and ``news_insight`` should come
    from Agent A (risk / personal finance analysis) and Agent B
    (news/trend analysis). ``user_question`` and ``history`` allow this
    endpoint to behave like a small decision-support chatbot.
    """

    finance_insight: str
    news_insight: str
    user_question: Optional[str] = None
    history: list[ChatTurn] = Field(default_factory=list)


class RecommendationResponse(BaseModel):
    recommendation: str
    rationale: str
    retrieved_knowledge: list[str]
    llm_powered: bool = False  # True when LLM was used, False for fallback


def _build_context(payload: RecommendationRequest) -> str:
    """Build a compact text context from Agent A/B + chat history."""

    parts: list[str] = []
    parts.append("Personal finance insight: " + payload.finance_insight.strip())
    parts.append("News & market insight: " + payload.news_insight.strip())

    if payload.history:
        turns: list[str] = []
        for turn in payload.history[-6:]:
            prefix = "User" if turn.role == "user" else "Assistant"
            turns.append(f"{prefix}: {turn.content.strip()}")
        parts.append("Recent conversation:\n" + "\n".join(turns))

    if payload.user_question:
        parts.append("Current user question: " + payload.user_question.strip())

    return "\n".join(parts)


@router.post("/synthesize", response_model=RecommendationResponse)
async def synthesize(payload: RecommendationRequest) -> RecommendationResponse:
    """Agent C: decision synthesizer + lightweight chatbot.

    Two-tier pipeline:
    Tier 1 (Traditional): RAG retrieval from knowledge corpus.
    Tier 2 (Agentic): LLM generates recommendation from context + RAG,
                      then self-critiques and refines. Falls back to
                      deterministic logic if LLM is unavailable.
    """

    context = _build_context(payload)

    # ---- RAG Retrieval (Tier 1) ----
    query_parts = [payload.finance_insight, payload.news_insight]
    if payload.user_question:
        query_parts.append(payload.user_question)
    query = " ".join(p for p in query_parts if p)

    chunks = retrieve(query, k=3)
    retrieved_texts = [f"{c.title}: {c.content}" for c in chunks]

    # ---- LLM-powered synthesis (Tier 2: Agentic reasoning) ----
    try:
        groq = get_groq_client()

        system_prompt = (
            "You are Agent C, a helpful financial AI chatbot. You combine:\n"
            "1. Personal finance analysis from Agent A (ML-based risk scoring)\n"
            "2. News & market analysis from Agent B (FinBERT NLP sentiment)\n"
            "3. Retrieved knowledge from a RAG financial knowledge base\n\n"
            "Rules:\n"
            "- Answer the user's SPECIFIC question directly. Do NOT give generic advice.\n"
            "- If they ask about budgeting, talk about budgeting. If about stocks, talk about stocks.\n"
            "- Reference actual data from the context provided.\n"
            "- Use INR (₹) for amounts.\n"
            "- Be conversational and concise (4-8 sentences unless more detail is needed).\n"
            "- If the user is having a casual conversation, respond naturally.\n"
            "- Never repeat the same advice twice in a conversation.\n"
        )

        knowledge_context = "\n".join(
            f"- {t}" for t in retrieved_texts
        ) if retrieved_texts else "No specific knowledge retrieved."

        # Build proper message history for multi-turn conversation
        messages: list[dict] = []

        # Add context as the first user message
        context_msg = (
            f"[Context for Agent C — do not repeat this verbatim]\n"
            f"Finance: {payload.finance_insight}\n"
            f"News: {payload.news_insight}\n"
            f"Knowledge: {knowledge_context}"
        )
        messages.append({"role": "user", "content": context_msg})
        messages.append({"role": "assistant", "content": "Got it, I have your financial context. How can I help?"})

        # Add actual conversation history as proper message turns
        if payload.history:
            for turn in payload.history[-10:]:
                messages.append({"role": turn.role, "content": turn.content})
        elif payload.user_question:
            messages.append({"role": "user", "content": payload.user_question})

        final_text = await groq.chat_with_history(
            system_prompt, messages, max_tokens=600
        )

        if final_text is None:
            raise RuntimeError("LLM call returned None")

        rationale = (
            "Agent C answered using LLM with full conversation history, "
            "Agent A risk analysis, Agent B news sentiment, and RAG knowledge. "
            f"Knowledge sources: {[c.title for c in chunks]}."
        )

        return RecommendationResponse(
            recommendation=final_text,
            rationale=rationale,
            retrieved_knowledge=retrieved_texts,
            llm_powered=True,
        )

    except Exception as exc:
        print(f"[Agent C] LLM synthesis failed, falling back to rule-based: {exc}")

    # ---- Fallback: question-aware deterministic response ----
    q = (payload.user_question or "").lower().strip()

    if not q:
        answer = _initial_draft_advice(context, [c.title for c in chunks])
    else:
        # Build a relevant answer based on the question keywords
        answer_parts: list[str] = []

        if any(w in q for w in ["budget", "spend", "expense", "cut", "save", "saving"]):
            answer_parts.append(
                "For budgeting, start with the 50-30-20 rule: 50% needs, 30% wants, 20% savings. "
                "Track every expense for a month to find leaks. "
                "Common quick wins: cook at home more, review subscriptions, and negotiate bills."
            )
        if any(w in q for w in ["invest", "sip", "mutual", "stock", "share", "market", "portfolio"]):
            answer_parts.append(
                "For investing, consider starting with index fund SIPs of ₹500-5000/month. "
                "Diversify across equity, debt, and gold. "
                "Never invest money you'll need within 3 years in equities."
            )
        if any(w in q for w in ["debt", "loan", "emi", "credit", "interest"]):
            answer_parts.append(
                "Prioritise paying off high-interest debt first (credit cards > personal loans > education loans). "
                "Consider the avalanche method: pay minimums on all, then throw extra at the highest-rate debt."
            )
        if any(w in q for w in ["emergency", "fund", "safety", "backup"]):
            answer_parts.append(
                "Build an emergency fund of 3-6 months of essential expenses. "
                "Keep it in a liquid fund or high-interest savings account, not in equities."
            )
        if any(w in q for w in ["risk", "risky", "safe", "aggressive", "conservative"]):
            answer_parts.append(
                "Your risk tolerance depends on your age, income stability, and financial goals. "
                "If risk is high, keep 60-70% in debt instruments and only 30-40% in equities."
            )
        if any(w in q for w in ["what if", "what-if", "scenario", "simulate"]):
            answer_parts.append(
                f"Based on your scenario: '{payload.user_question}' — "
                "This would impact your monthly cash flow. Consider running the numbers: "
                "calculate the exact monthly difference, then check if you'd still have "
                "positive savings after the change."
            )

        if not answer_parts:
            answer_parts.append(
                f"Regarding your question about '{payload.user_question}': "
                "Based on your financial context, I'd suggest reviewing your monthly budget, "
                "ensuring your emergency fund is adequate, and aligning any decisions with "
                "your risk level. For more specific advice, try asking about budgeting, "
                "investing, debt management, or risk assessment."
            )

        answer = " ".join(answer_parts)

        # Append relevant RAG knowledge
        if retrieved_texts:
            answer += " Related tips: " + "; ".join(c.title for c in chunks) + "."

    return RecommendationResponse(
        recommendation=answer,
        rationale=f"Deterministic fallback (LLM unavailable). Knowledge: {[c.title for c in chunks]}.",
        retrieved_knowledge=retrieved_texts,
        llm_powered=False,
    )
