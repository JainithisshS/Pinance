from datetime import date, datetime
from calendar import monthrange
from typing import Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from ..db import get_connection, insert_risk_log
from ..auth import get_current_user
from ..risk_model import MonthlyFeatures, explain_risk, predict_risk
from ..services.groq_client import get_groq_client
from .transactions import is_credit_message


router = APIRouter()


class TransactionSummary(BaseModel):
    total_spent: float
    transactions_count: int
    top_category: Optional[str]
    start_date: date
    end_date: date
    total_income: float = 0.0
    total_expenses: float = 0.0
    savings: float = 0.0


class FinanceAnalysisRequest(BaseModel):
    start_date: date
    end_date: date


class FinanceAnalysisResponse(BaseModel):
    summary: TransactionSummary
    risk_level: str
    message: str
    ml_risk_level: Optional[str] = None
    ml_risk_confidence: Optional[float] = None
    ml_risk_explanation: Optional[str] = None
    ml_confidence_band: Optional[str] = None
    llm_insight: Optional[str] = None  # LLM-generated personalized analysis


class ExpenseCategorySummary(BaseModel):
    """Aggregate spend for a category over a date range.

    Used by the dashboard's expense breakdown card to show
    where money is going (e.g., Food, Rent, EMI).
    """

    category: str
    total_spent: float
    transactions_count: int


@router.post("/analyze_finance", response_model=FinanceAnalysisResponse)
async def analyze_finance(payload: FinanceAnalysisRequest, user_id: str = Depends(get_current_user)) -> FinanceAnalysisResponse:
    """Simple personal finance analysis for a date range.

    - Reads transactions from SQLite between start_date and end_date.
    - Computes total amount spent, number of transactions, and top category.
    - Derives a naive risk level based on total spending.
    """

    # Convert date range to ISO strings compatible with stored timestamps.
    start_dt = datetime.combine(payload.start_date, datetime.min.time())
    end_dt = datetime.combine(payload.end_date, datetime.max.time())

    conn = get_connection()
    try:
        rows = conn.execute(
            """
            SELECT amount, category, timestamp, raw_message
            FROM transactions
            WHERE user_id = ? AND timestamp BETWEEN ? AND ?
            """,
            (user_id, start_dt.isoformat(), end_dt.isoformat()),
        ).fetchall()
    finally:
        conn.close()

    if not rows:
        summary = TransactionSummary(
            total_spent=0.0,
            transactions_count=0,
            top_category=None,
            start_date=payload.start_date,
            end_date=payload.end_date,
            total_income=0.0,
            total_expenses=0.0,
            savings=0.0,
        )
        return FinanceAnalysisResponse(
            summary=summary,
            risk_level="low",
            message="No transactions found in the selected period.",
        )

    # How much of the month is covered by this range?
    # This helps us avoid overconfident "low risk" messages very early in the month.
    days_in_month = monthrange(payload.end_date.year, payload.end_date.month)[1]
    days_covered = (payload.end_date - payload.start_date).days + 1
    if days_covered < 0:
        days_covered = 0
    coverage_ratio = max(0.0, min(days_covered / days_in_month, 1.0))
    month_progress_pct = (payload.end_date.day / days_in_month) * 100.0
    days_remaining = max(days_in_month - payload.end_date.day, 0)
    total_income = 0.0
    total_expenses = 0.0
    transactions_count = len(rows)

    # Count categories and split fixed vs variable based on category names
    category_counts: dict[str, int] = {}
    fixed_expenses_total = 0.0
    variable_expenses_total = 0.0
    for row in rows:
        cat = row["category"] or "Other"
        category_counts[cat] = category_counts.get(cat, 0) + 1
        amount = float(row["amount"] or 0.0)
        is_credit = is_credit_message(row["raw_message"] or "")
        if is_credit:
            total_income += amount
        else:
            total_expenses += amount

            # Very simple fixed vs variable mapping based on category
            cat_lower = cat.lower()
            if any(k in cat_lower for k in ["rent", "emi", "bill", "utility", "subscription", "bills & utilities", "education"]):
                fixed_expenses_total += amount
            else:
                variable_expenses_total += amount

    total_spent = total_expenses  # backward-compatible field
    savings = total_income - total_expenses

    top_category = max(category_counts, key=category_counts.get) if category_counts else None

    summary = TransactionSummary(
        total_spent=total_spent,
        transactions_count=transactions_count,
        top_category=top_category,
        start_date=payload.start_date,
        end_date=payload.end_date,
        total_income=total_income,
        total_expenses=total_expenses,
        savings=savings,
    )

    # Calculate days coverage for better risk assessment
    days_in_range = (payload.end_date - payload.start_date).days + 1
    # Estimate days in month (use 30 as average)
    estimated_days_in_month = 30
    coverage_ratio = min(days_in_range / estimated_days_in_month, 1.0)
    
    # Prorate expenses to monthly equivalent for fair risk assessment
    # If we're only 13 days into the month, project what the full month might look like
    if coverage_ratio < 1.0 and coverage_ratio > 0:
        projected_monthly_expenses = total_expenses / coverage_ratio
    else:
        projected_monthly_expenses = total_expenses
    
    # Baseline heuristic risk using PROJECTED monthly expenses
    # This prevents showing "low risk" just because we're early in the month
    if projected_monthly_expenses < 5000:
        heuristic_risk = "low"
    elif projected_monthly_expenses < 20000:
        heuristic_risk = "medium"
    else:
        heuristic_risk = "high"

    # Build feature vector for ML risk model (if available)
    # Prefer category-based fixed/variable totals; fall back to a simple guess
    if fixed_expenses_total + variable_expenses_total > 0:
        fixed_guess = fixed_expenses_total
        variable_guess = variable_expenses_total
    else:
        fixed_guess = total_income * 0.35
        fixed_guess = min(max(fixed_guess, 0.0), total_expenses)
        variable_guess = max(total_expenses - fixed_guess, 0.0)
    savings_rate = savings / total_income if total_income > 0 else 0.0
    variable_share = variable_guess / total_income if total_income > 0 else 0.0

    ml_label: Optional[str] = None
    ml_conf: Optional[float] = None
    ml_expl: Optional[str] = None
    ml_band: Optional[str] = None
    
    # For ML model, use projected monthly values if we're in a partial month
    if coverage_ratio < 1.0 and coverage_ratio > 0:
        ml_income = total_income / coverage_ratio
        ml_savings = ml_income - projected_monthly_expenses
    else:
        ml_income = total_income
        ml_savings = savings

    ml_result = predict_risk(
        MonthlyFeatures(
            income=ml_income,
            fixed_expenses=fixed_guess,
            variable_expenses=variable_guess,
            savings=ml_savings,
            savings_rate=ml_savings / ml_income if ml_income > 0 else 0.0,
            variable_share=variable_guess / ml_income if ml_income > 0 else 0.0,
        )
    )
    if ml_result is not None:
        label, conf = ml_result
        ml_label = label
        ml_conf = conf
        # Simple confidence/uncertainty band for UI and messaging
        if ml_conf >= 0.75:
            ml_band = "high"
        elif ml_conf >= 0.5:
            ml_band = "medium"
        else:
            ml_band = "low"

        # If we are very early in the month, downgrade certainty a bit
        # so users don't see an overconfident "low risk" just because
        # there are only a few days of data.
        if coverage_ratio < 0.3 and ml_band == "high":
            ml_band = "medium"
        if coverage_ratio < 0.15:
            ml_band = "low"
        ml_expl = explain_risk(
            label,
            MonthlyFeatures(
                income=total_income,
                fixed_expenses=fixed_guess,
                variable_expenses=variable_guess,
                savings=savings,
                savings_rate=savings_rate,
                variable_share=variable_share,
            ),
        )
        risk_level = ml_label
    else:
        # Fall back to heuristic if ML model is not available
        risk_level = heuristic_risk

    message_parts = [
        f"You received INR {total_income:.2f} and spent INR {total_expenses:.2f} across {transactions_count} transactions.",
        f"Most common category: {top_category or 'N/A'}.",
        f"Approximate savings/balance for this period: {savings:.2f}.",
    ]

    if ml_label is not None:
        band_text = f", {ml_band} certainty" if ml_band is not None else ""
        message_parts.append(
            f"ML-based risk level for this period: {ml_label} (≈ {ml_conf * 100:.0f}% confidence{band_text})."
        )
        if ml_expl:
            message_parts.append(ml_expl)
        if coverage_ratio < 0.3:
            message_parts.append(
                f"Note: it's still early in the month (about {month_progress_pct:.0f}% through, ~{days_remaining} days remaining), so this risk view is provisional and may change as more transactions arrive."
            )
    else:
        message_parts.append(f"Heuristic risk level for this period: {risk_level}.")

    message = " ".join(message_parts)

    # Log the risk evaluation for offline analysis
    try:
        insert_risk_log(
            {
                "user_id": user_id,
                "created_at": datetime.utcnow().isoformat(),
                "start_date": payload.start_date.isoformat(),
                "end_date": payload.end_date.isoformat(),
                "total_income": total_income,
                "total_expenses": total_expenses,
                "savings": savings,
                "heuristic_risk": heuristic_risk,
                "ml_risk_level": ml_label,
                "ml_risk_confidence": ml_conf,
            }
        )
    except Exception:
        # Logging must never break the main API response
        pass

    # ---- LLM-powered insight (Tier 2: Agentic reasoning) ----
    llm_insight: Optional[str] = None
    try:
        groq = get_groq_client()
        system_prompt = (
            "You are Agent A, a personal finance analyst AI for an Indian user. "
            "You receive structured financial data computed by traditional ML models "
            "(scikit-learn risk classifier, heuristic rules). Your job is to reason "
            "about this data and produce a short, personalized, actionable financial "
            "insight. Be specific about numbers. Use INR (₹). "
            "Keep it to 4-5 sentences. Do NOT give generic advice — "
            "refer to the actual numbers provided."
        )
        # Build a category breakdown string
        cat_breakdown = ", ".join(
            f"{cat}: {cnt} txns" for cat, cnt in sorted(
                category_counts.items(), key=lambda x: -x[1]
            )[:5]
        ) if category_counts else "No categories"

        user_prompt = (
            f"Here is the financial data for {payload.start_date} to {payload.end_date}:\n"
            f"- Total Income: ₹{total_income:,.2f}\n"
            f"- Total Expenses: ₹{total_expenses:,.2f}\n"
            f"- Savings: ₹{savings:,.2f}\n"
            f"- Savings Rate: {savings_rate * 100:.1f}%\n"
            f"- Transactions: {transactions_count}\n"
            f"- Top categories: {cat_breakdown}\n"
            f"- Fixed expenses: ₹{fixed_expenses_total:,.2f}, Variable: ₹{variable_expenses_total:,.2f}\n"
            f"- ML Risk Level: {ml_label or heuristic_risk} "
            f"(confidence: {ml_conf * 100:.0f}%)\n" if ml_conf else
            f"- Heuristic Risk Level: {heuristic_risk}\n"
            f"- Month coverage: {days_covered} of {days_in_month} days ({coverage_ratio * 100:.0f}%)\n"
            f"\nProvide a personalized financial insight based on this data."
        )
        llm_insight = await groq.chat(system_prompt, user_prompt, max_tokens=400)
    except Exception as exc:
        print(f"[Agent A] LLM insight generation failed: {exc}")

    return FinanceAnalysisResponse(
        summary=summary,
        risk_level=risk_level,
        message=message,
        ml_risk_level=ml_label,
        ml_risk_confidence=ml_conf,
        ml_risk_explanation=ml_expl,
        ml_confidence_band=ml_band,
        llm_insight=llm_insight,
    )


class RiskLogsSummary(BaseModel):
    total_logs: int
    by_risk_level: dict[str, int]
    avg_income: float
    avg_expenses: float
    avg_savings: float


@router.get("/risk_logs_summary", response_model=RiskLogsSummary)
async def risk_logs_summary(user_id: str = Depends(get_current_user)) -> RiskLogsSummary:
    """Lightweight summary over stored risk_logs for evaluation.

    Aggregates count per (ML) risk level and basic averages so you
    can quickly inspect how Agent A is behaving over time.
    """

    conn = get_connection()
    try:
        rows = conn.execute(
            """
            SELECT
                total_income,
                total_expenses,
                savings,
                COALESCE(ml_risk_level, heuristic_risk) AS risk_level
            FROM risk_logs
            WHERE user_id = ?
            """,
            (user_id,)
        ).fetchall()
    finally:
        conn.close()

    if not rows:
        return RiskLogsSummary(
            total_logs=0,
            by_risk_level={},
            avg_income=0.0,
            avg_expenses=0.0,
            avg_savings=0.0,
        )

    total_logs = len(rows)
    by_risk: dict[str, int] = {}
    sum_income = 0.0
    sum_expenses = 0.0
    sum_savings = 0.0

    for row in rows:
        risk = row["risk_level"] or "unknown"
        by_risk[risk] = by_risk.get(risk, 0) + 1
        sum_income += float(row["total_income"] or 0.0)
        sum_expenses += float(row["total_expenses"] or 0.0)
        sum_savings += float(row["savings"] or 0.0)

    return RiskLogsSummary(
        total_logs=total_logs,
        by_risk_level=by_risk,
        avg_income=sum_income / total_logs,
        avg_expenses=sum_expenses / total_logs,
        avg_savings=sum_savings / total_logs,
    )


@router.post("/expense_breakdown", response_model=list[ExpenseCategorySummary])
async def expense_breakdown(payload: FinanceAnalysisRequest, user_id: str = Depends(get_current_user)) -> list[ExpenseCategorySummary]:
    """Category-wise expense breakdown for a date range.

    This powers the dashboard's bar-chart style card showing
    how spending is distributed across categories.
    """

    start_dt = datetime.combine(payload.start_date, datetime.min.time())
    end_dt = datetime.combine(payload.end_date, datetime.max.time())

    conn = get_connection()
    try:
        rows = conn.execute(
            """
            SELECT category, amount, raw_message
            FROM transactions
            WHERE user_id = ? AND timestamp BETWEEN ? AND ?
            """,
            (user_id, start_dt.isoformat(), end_dt.isoformat()),
        ).fetchall()
    finally:
        conn.close()

    aggregates: dict[str, ExpenseCategorySummary] = {}

    for row in rows:
        cat = row["category"] or "Other"
        amount = float(row["amount"] or 0.0)
        if is_credit_message(row["raw_message"] or ""):
            # Skip income for expense breakdown
            continue
        if cat not in aggregates:
            aggregates[cat] = ExpenseCategorySummary(
                category=cat,
                total_spent=0.0,
                transactions_count=0,
            )
        summary = aggregates[cat]
        summary.total_spent += amount
        summary.transactions_count += 1

    return list(aggregates.values())
