import re
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends

from ..db import init_db, insert_transaction, get_connection
from ..models import ParseMessageRequest, Transaction
from ..category_model import predict_category
from ..auth import get_current_user


router = APIRouter()


@router.on_event("startup")
async def startup_event() -> None:
    init_db()


@router.get("/health")
async def health_check():
    return {"status": "ok"}


# Enhanced pattern to handle multiple SMS formats:
# 1. Standard: "Rs 500", "INR 1,250.50", "Rs.750"
# 2. Rupees suffix: "500 rupees"
# 3. After keywords: "Debited: 1000", "Amount: 500"
AMOUNT_PATTERN = re.compile(
    r"(?:inr|rs\.?|rs\s*\.)\s*([0-9,]+\.?[0-9]*)|"  # Group 1: Standard Rs/INR prefix
    r"([0-9,]+\.?[0-9]*)\s*rupees|"  # Group 2: "500 rupees" format
    r"(?:debited|credited|spent|amount|transaction)[\s:]+([0-9,]+\.?[0-9]*)",  # Group 3: After keywords
    re.IGNORECASE  # Case-insensitive flag
)
MERCHANT_PATTERN = re.compile(r"at\s+([A-Za-z0-9 &.-]+)")


COMMON_CATEGORIES = [
    "Food & Dining",
    "Transport",
    "Shopping",
    "Bills & Utilities",
    "Groceries",
    "Entertainment",
    "Health",
    "Education",
    "Investment",
    "Other",
]


CATEGORY_KEYWORDS = {
    "Food & Dining": [
        "swiggy",
        "zomato",
        "dominos",
        "pizza",
        "restaurant",
        "cafe",
    ],
    "Transport": [
        "uber",
        "ola",
        "rapido",
        "metro",
        "bus",
        "train",
        "cab",
        "taxi",
    ],
    "Shopping": [
        "amazon",
        "flipkart",
        "myntra",
        "ajio",
        "mall",
        "lifestyle",
    ],
    "Bills & Utilities": [
        "electricity",
        "power",
        "water",
        "gas",
        "dth",
        "broadband",
        "wifi",
        "mobile bill",
        "postpaid",
    ],
    "Groceries": [
        "bigbasket",
        "blinkit",
        "grofer",
        "grocery",
        "mart",
        "supermarket",
    ],
    "Entertainment": [
        "netflix",
        "hotstar",
        "spotify",
        "cinema",
        "pvr",
        "inox",
    ],
    "Health": [
        "pharmacy",
        "chemist",
        "apollo",
        "medplus",
        "hospital",
        "clinic",
    ],
    "Education": [
        "coursera",
        "udemy",
        "byju",
        "coaching",
        "school",
        "college",
        "university",
    ],
    "Investment": [
        "zerodha",
        "upstox",
        "groww",
        "nse",
        "bse",
        "mutual fund",
        "sip",
    ],
}


_CREDIT_KEYWORDS = [
    "credited",
    "credit",
    "has been credited",
    "salary",
    "payment received",
    "received",
    "refund",
    "cashback",
    "reversal",
    "deposit",
    "neft credit",
    "imps credit",
    "upi credit",
]

_DEBIT_KEYWORDS = [
    "debited",
    "debit",
    "has been debited",
    "spent",
    "purchase",
    "payment made",
    "upi payment",
    "sent to",
    "withdrawn",
    "atm wdl",
]


def is_credit_message(raw_message: str) -> bool:
    """Heuristic check to see if an SMS represents income (credit).

    This is shared between parsing and analysis logic so that
    income/expense treatment stays consistent.
    """

    text = raw_message.lower()
    if any(keyword in text for keyword in _CREDIT_KEYWORDS):
        return True
    if any(keyword in text for keyword in _DEBIT_KEYWORDS):
        return False
    return False


def _parse_amount(message: str) -> Optional[float]:
    match = AMOUNT_PATTERN.search(message)
    if not match:
        return None
    # Check all three capture groups (one for each pattern alternative)
    value = match.group(1) or match.group(2) or match.group(3)
    if value:
        value = value.replace(",", "")
        try:
            return float(value)
        except ValueError:
            return None
    return None


def _parse_merchant(message: str) -> Optional[str]:
    match = MERCHANT_PATTERN.search(message)
    if match:
        return match.group(1).strip()
    return None


def _infer_category(message: str, merchant: Optional[str]) -> str:
    """Infer a common category from merchant/message.

    Always returns one of COMMON_CATEGORIES, defaulting to "Other".
    """

    text = (merchant or "") + " " + message
    text = text.lower()

    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(keyword in text for keyword in keywords):
            return category

    return "Other"


@router.post("/parse_message", response_model=Transaction)
async def parse_message(
    payload: ParseMessageRequest,
    user_id: str = Depends(get_current_user)
) -> Transaction:
    amount = _parse_amount(payload.raw_message)
    merchant = _parse_merchant(payload.raw_message)
    # First do a quick rule-based inference so we always have
    # a sensible default category.
    base_category = _infer_category(payload.raw_message, merchant)

    # Then let the ML model refine it, falling back to base_category
    # if the model is missing or not confident enough.
    category = predict_category(payload.raw_message, default=base_category)

    if payload.timestamp is None:
        timestamp = datetime.utcnow()
    else:
        timestamp = payload.timestamp

    transaction = Transaction(
        amount=amount or 0.0,
        merchant=merchant,
        category=category,
        timestamp=timestamp,
        raw_message=payload.raw_message,
    )

    db_data = {
        "user_id": user_id,
        "amount": transaction.amount,
        "merchant": transaction.merchant,
        "category": transaction.category,
        "currency": transaction.currency,
        "timestamp": transaction.timestamp.isoformat(),
        "raw_message": transaction.raw_message,
    }
    new_id = insert_transaction(db_data)
    transaction.id = new_id

    return transaction


@router.get("/transactions", response_model=list[Transaction])
async def list_transactions(
    limit: int = 50,
    user_id: str = Depends(get_current_user)
) -> list[Transaction]:
    """Return recent parsed transactions (SMS history).

    This is used by the app to show a history of both
    manually-pasted and notification-captured messages.
    """

    conn = get_connection()
    try:
        rows = conn.execute(
            """
            SELECT id, amount, merchant, category, currency, timestamp, raw_message
            FROM transactions
            WHERE user_id = ?
            ORDER BY datetime(timestamp) DESC
            LIMIT ?
            """,
            (user_id, limit),
        ).fetchall()
    finally:
        conn.close()

    return [
        Transaction(
            id=row["id"],
            amount=float(row["amount"] or 0.0),
            merchant=row["merchant"],
            category=row["category"],
            currency=row["currency"] or "INR",
            timestamp=datetime.fromisoformat(row["timestamp"]),
            raw_message=row["raw_message"],
        )
        for row in rows
    ]


