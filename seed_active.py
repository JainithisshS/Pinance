import os
from datetime import datetime, timedelta
from dotenv import load_dotenv
from supabase import create_client, Client

load_dotenv('backend/.env')

url = os.environ.get("SUPABASE_URL")
key = os.environ.get("SUPABASE_SERVICE_KEY")
supabase: Client = create_client(url, key)

user_id = "wDRlV845LDcNJbArxZth54dzAZH2"
now = datetime.now()

# 1. Clear old transactions for this user so we don't duplicate
try:
    deleted = supabase.table("transactions").delete().eq("user_id", user_id).execute()
    print(f"Cleared {len(deleted.data)} old transactions")
except Exception as e:
    print(f"Error clearing: {e}")

# 2. Re-insert with proper dates (all within the last 6 days so they show up in the current month)
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

inserted = 0
for i, txn in enumerate(dummy_transactions):
    # Spread transactions over the last 5 days (so ALL of them fall in March)
    txn_date = now - timedelta(hours=i * 8)
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
        supabase.table("transactions").insert(db_data).execute()
        inserted += 1
        print(f"Inserted: {txn['amount']} - {txn['category']}")
    except Exception as e:
        print(f"Failed: {e}")

print(f"Done! Inserted {inserted} transactions for Google Account user {user_id}")
