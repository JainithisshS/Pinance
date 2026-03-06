import re
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel as _BaseModel

from ..db import init_db, insert_transaction, get_connection, update_transaction_category, get_user_transactions, delete_user_transactions
from ..models import ParseMessageRequest, Transaction
from ..category_model import predict_category
from ..auth import get_current_user
import os


router = APIRouter()


@router.on_event("startup")
async def startup_event() -> None:
    init_db()
    # One-time cleanup: remove junk transactions (amount=0 or non-financial)
    _cleanup_junk_transactions()


def _cleanup_junk_transactions():
    """Delete all transactions with amount=0 (junk from non-SMS notifications)."""
    USE_SUPABASE = bool(os.getenv("SUPABASE_URL"))
    if USE_SUPABASE:
        try:
            from ..supabase_client import get_supabase
            supabase = get_supabase()
            result = supabase.table("transactions").delete().eq("amount", 0).execute()
            print(f"[CLEANUP] Deleted {len(result.data)} junk transactions (amount=0) from Supabase")
            # Also delete all existing transactions to start fresh
            result2 = supabase.table("transactions").delete().gt("id", 0).execute()
            print(f"[CLEANUP] Cleared all {len(result2.data)} old transactions from Supabase")
        except Exception as e:
            print(f"[CLEANUP] Supabase cleanup failed: {e}")
    else:
        import sqlite3
        from pathlib import Path
        db_path = Path(__file__).parent.parent / "transactions.db"
        try:
            conn = sqlite3.connect(db_path)
            cursor = conn.execute("DELETE FROM transactions")
            conn.commit()
            print(f"[CLEANUP] Deleted {cursor.rowcount} transactions from SQLite")
            conn.close()
        except Exception as e:
            print(f"[CLEANUP] SQLite cleanup failed: {e}")


@router.get("/health")
async def health_check():
    return {"status": "ok"}


# Enhanced pattern to handle multiple SMS formats:
# 1. Standard: "Rs 500", "INR 1,250.50", "Rs.750"
# 2. Rupees suffix: "500 rupees"
# 3. After keywords: "Debited: 1000", "Amount: 500"
AMOUNT_PATTERN = re.compile(
    # 1) Currency prefix: Rs / INR / ₹  followed by number
    r"(?:inr|rs\.?|rs\s*\.|₹)\s*([0-9,]+\.?[0-9]*)|"
    # 2) Number followed by 'rupees'
    r"([0-9,]+\.?[0-9]*)\s*rupees|"
    # 3) Keywords like 'debited' or 'credited' possibly followed by small words ('by','for') then a number
    r"(?:debited|credited|spent|amount|transaction)(?:\s*(?:by|for|of|:)?\s*)([0-9,]+\.?[0-9]*)",
    re.IGNORECASE,
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


# ── Spam / promotional patterns that should NEVER be treated as transactions ──
_SPAM_KEYWORDS = [
    "cashback offer", "subscribe now", "limited time",
    "congratulations", "claim now", "hurry",
    "coupon", "discount code", "promo code",
    "otp", "one time password",
    "verification code", "login code",
    "win big", "lucky draw",
]

# Terms that strongly signal a real bank SMS
_BANK_SIGNALS = [
    "a/c", "acct", "account", "upi", "neft", "imps", "rtgs",
    "atm", "bank", "bal", "avl bal", "available balance",
    "xxxx", "ending",
]


def is_financial_sms(message: str) -> bool:
    """Return True only for genuine debit / credit bank SMS.

    Requires:
      1. A recognisable money amount (Rs / INR / rupees + number)
      2. At least one debit/credit keyword **or** one strong bank signal
    Rejects only if message is clearly spam (multiple spam-only keywords
    with NO bank signals at all).
    """
    text = message.lower()

    # ── Must contain a money amount ──
    if AMOUNT_PATTERN.search(message) is None:
        return False

    # ── Must contain a debit/credit keyword OR a bank signal term ──
    has_txn_keyword = any(kw in text for kw in _CREDIT_KEYWORDS) or \
                      any(kw in text for kw in _DEBIT_KEYWORDS)
    has_bank_signal = any(kw in text for kw in _BANK_SIGNALS)

    if not has_txn_keyword and not has_bank_signal:
        return False

    # ── Reject only if clearly spam AND no bank signals ──
    spam_hits = sum(1 for kw in _SPAM_KEYWORDS if kw in text)
    if spam_hits >= 2 and not has_bank_signal and not has_txn_keyword:
        return False

    return True


def _parse_amount(message: str) -> Optional[float]:
    # Primary attempt: the comprehensive AMOUNT_PATTERN
    match = AMOUNT_PATTERN.search(message)
    if match:
        # match groups: group(1) or group(2) or group(3)
        value = None
        try:
            value = match.group(1) or match.group(2) or match.group(3)
        except IndexError:
            value = None
        if value:
            value = value.replace(",", "")
            try:
                return float(value)
            except ValueError:
                return None

    # Fallback: look specifically for 'debited' or 'credited' followed by up to 15 non-digit chars then a number
    kb = re.search(r"(?:debited|credited)\D{0,15}([0-9,]+\.?[0-9]*)", message, re.IGNORECASE)
    if kb:
        value = kb.group(1).replace(",", "")
        try:
            return float(value)
        except ValueError:
            return None

    # Last resort: any standalone number with at least two digits and optional decimals
    anynum = re.search(r"([0-9]{2,}[0-9,]*\.?[0-9]*)", message)
    if anynum:
        v = anynum.group(1).replace(",", "")
        try:
            return float(v)
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
    print(f"[parse_message] user={user_id} raw={payload.raw_message[:200]}")

    amount = _parse_amount(payload.raw_message)
    print(f"[parse_message] parsed_amount={amount}")
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

    rows = get_user_transactions(user_id, limit)

    return [
        Transaction(
            id=row["id"],
            amount=float(row["amount"] or 0.0),
            merchant=row.get("merchant"),
            category=row.get("category"),
            currency=row.get("currency") or "INR",
            timestamp=datetime.fromisoformat(str(row["timestamp"])),
            raw_message=row.get("raw_message", ""),
        )
        for row in rows
    ]


class UpdateCategoryRequest(_BaseModel):
    category: str


@router.put("/transactions/{transaction_id}/category")
async def update_category(
    transaction_id: int,
    payload: UpdateCategoryRequest,
    user_id: str = Depends(get_current_user),
):
    """Allow user to manually change the category of a transaction."""
    updated = update_transaction_category(transaction_id, user_id, payload.category)
    if not updated:
        raise HTTPException(status_code=404, detail="Transaction not found")
    return {"status": "ok", "id": transaction_id, "category": payload.category}


@router.delete("/transactions")
async def clear_transactions(
    user_id: str = Depends(get_current_user),
):
    """Delete ALL transactions for the current user."""
    deleted = delete_user_transactions(user_id)
    return {"status": "ok", "deleted": deleted}


@router.post("/seed_dummy")
async def seed_dummy_transactions(
    user_id: str = Depends(get_current_user),
):
    """Insert realistic dummy income & expense transactions for demo/testing."""
    from datetime import timedelta
    now = datetime.now()

    dummy_transactions = [
        # ── INCOME ──
        {"amount": 65000.0, "merchant": "Employer Pvt Ltd",    "category": "Income",          "raw_message": "Salary credited Rs 65,000 to your A/C XXXX1234 by NEFT from Employer Pvt Ltd"},
        {"amount": 12000.0, "merchant": "Freelance Client",    "category": "Income",          "raw_message": "Rs 12,000 credited to your A/C via UPI from freelance-client@oksbi"},
        {"amount": 2500.0,  "merchant": "Zerodha Dividend",    "category": "Income",          "raw_message": "INR 2,500 credited — dividend payout from Zerodha demat account"},
        # ── EXPENSES ──
        {"amount": 450.0,   "merchant": "Swiggy",              "category": "Food & Dining",   "raw_message": "Rs 450 debited from A/C XXXX1234 for Swiggy order at Pizza Hut"},
        {"amount": 280.0,   "merchant": "Uber India",          "category": "Transport",       "raw_message": "INR 280 debited for Uber trip from Home to Office via UPI"},
        {"amount": 1999.0,  "merchant": "Amazon",              "category": "Shopping",        "raw_message": "Rs 1,999 debited for Amazon.in purchase — Wireless Earbuds"},
        {"amount": 15000.0, "merchant": "Landlord",            "category": "Bills & Utilities","raw_message": "Rs 15,000 debited — monthly rent transfer to landlord via NEFT"},
        {"amount": 1800.0,  "merchant": "Tata Power",          "category": "Bills & Utilities","raw_message": "INR 1,800 debited for Tata Power electricity bill payment"},
        {"amount": 649.0,   "merchant": "Netflix",             "category": "Entertainment",   "raw_message": "Rs 649 debited for Netflix India monthly subscription renewal"},
        {"amount": 2350.0,  "merchant": "BigBasket",           "category": "Groceries",       "raw_message": "Rs 2,350 debited for BigBasket grocery order — weekly essentials"},
        {"amount": 5000.0,  "merchant": "Zerodha",             "category": "Investment",      "raw_message": "INR 5,000 debited for SIP investment via Zerodha — Nifty 50 Index Fund"},
        {"amount": 350.0,   "merchant": "Zomato",              "category": "Food & Dining",   "raw_message": "Rs 350 debited via UPI for Zomato order — Biryani"},
        {"amount": 199.0,   "merchant": "Rapido",              "category": "Transport",       "raw_message": "INR 199 debited for Rapido bike ride — Mall to Home"},
        {"amount": 1500.0,  "merchant": "Apollo Pharmacy",     "category": "Health",          "raw_message": "Rs 1,500 debited at Apollo Pharmacy — medicines"},
        {"amount": 799.0,   "merchant": "Coursera",            "category": "Education",       "raw_message": "Rs 799 debited for Coursera monthly subscription — ML Specialization"},
    ]

    inserted = []
    for i, txn in enumerate(dummy_transactions):
        # Spread transactions over the last 25 days
        txn_date = now - timedelta(days=25 - i * 2)
        db_data = {
            "user_id": user_id,
            "amount": txn["amount"],
            "merchant": txn["merchant"],
            "category": txn["category"],
            "currency": "INR",
            "timestamp": txn_date.isoformat(),
            "raw_message": txn["raw_message"],
        }
        try:
            txn_id = insert_transaction(db_data)
            inserted.append({"id": txn_id, "amount": txn["amount"], "merchant": txn["merchant"], "category": txn["category"]})
        except Exception as e:
            print(f"[SEED] Failed to insert dummy txn: {e}")

    return {
        "status": "ok",
        "inserted": len(inserted),
        "transactions": inserted,
    }

