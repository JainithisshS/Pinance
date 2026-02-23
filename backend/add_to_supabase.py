"""Add dummy data directly to Supabase using the Supabase client."""
import os
import sys
from pathlib import Path
from dotenv import load_dotenv

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

# Load environment variables
env_path = Path(__file__).parent / ".env"
load_dotenv(dotenv_path=env_path)

from backend.supabase_client import get_supabase
from datetime import datetime, timedelta
import random

# Get Supabase client
supabase = get_supabase()

# Realistic transactions with proper timestamps
transactions = [
    # Food & Dining
    {"amount": 450.00, "merchant": "SWIGGY", "category": "Food & Dining", "currency": "INR", "timestamp": "2026-02-10T19:30:00", "raw_message": "Rs 450 debited at SWIGGY"},
    {"amount": 320.50, "merchant": "ZOMATO", "category": "Food & Dining", "currency": "INR", "timestamp": "2026-02-10T13:15:00", "raw_message": "Rs 320.50 spent at ZOMATO"},
    {"amount": 180.00, "merchant": "STARBUCKS", "category": "Food & Dining", "currency": "INR", "timestamp": "2026-02-09T10:00:00", "raw_message": "Rs 180 at STARBUCKS COFFEE"},
    {"amount": 250.00, "merchant": "MCDONALDS", "category": "Food & Dining", "currency": "INR", "timestamp": "2026-02-09T20:30:00", "raw_message": "Rs 250 at MCDONALDS"},
    {"amount": 890.00, "merchant": "DOMINOS", "category": "Food & Dining", "currency": "INR", "timestamp": "2026-02-08T19:00:00", "raw_message": "Rs 890 at DOMINOS PIZZA"},
    
    # Shopping
    {"amount": 2499.00, "merchant": "AMAZON", "category": "Shopping", "currency": "INR", "timestamp": "2026-02-09T15:20:00", "raw_message": "Rs 2,499 debited at AMAZON"},
    {"amount": 1850.00, "merchant": "FLIPKART", "category": "Shopping", "currency": "INR", "timestamp": "2026-02-08T11:00:00", "raw_message": "Rs 1,850 spent at FLIPKART"},
    {"amount": 3200.00, "merchant": "MYNTRA", "category": "Shopping", "currency": "INR", "timestamp": "2026-02-07T16:45:00", "raw_message": "Rs 3,200 at MYNTRA"},
    {"amount": 1500.00, "merchant": "RELIANCE DIGITAL", "category": "Shopping", "currency": "INR", "timestamp": "2026-02-07T14:00:00", "raw_message": "Rs 1,500 at RELIANCE DIGITAL"},
    {"amount": 1000.00, "merchant": "GIFT SHOP", "category": "Shopping", "currency": "INR", "timestamp": "2026-02-06T12:00:00", "raw_message": "Rs 1,000 GIFT CARD PURCHASE"},
    
    # Transport
    {"amount": 85.00, "merchant": "UBER", "category": "Transport", "currency": "INR", "timestamp": "2026-02-10T09:30:00", "raw_message": "Rs 85 debited by UPI/UBER"},
    {"amount": 120.00, "merchant": "OLA", "category": "Transport", "currency": "INR", "timestamp": "2026-02-09T18:00:00", "raw_message": "Rs 120 debited-OLA"},
    {"amount": 50.00, "merchant": "METRO", "category": "Transport", "currency": "INR", "timestamp": "2026-02-08T08:00:00", "raw_message": "Rs 50 METRO CARD RECHARGE"},
    {"amount": 300.00, "merchant": "PETROL PUMP", "category": "Transport", "currency": "INR", "timestamp": "2026-02-07T17:00:00", "raw_message": "Rs 300 for PETROL"},
    
    # Bills & Utilities
    {"amount": 1250.00, "merchant": "ELECTRICITY BOARD", "category": "Bills & Utilities", "currency": "INR", "timestamp": "2026-02-05T10:00:00", "raw_message": "Rs 1,250 ELECTRICITY BILL"},
    {"amount": 850.00, "merchant": "AIRTEL", "category": "Bills & Utilities", "currency": "INR", "timestamp": "2026-02-04T11:00:00", "raw_message": "Rs 850 AIRTEL PREPAID"},
    {"amount": 999.00, "merchant": "BROADBAND", "category": "Bills & Utilities", "currency": "INR", "timestamp": "2026-02-03T09:00:00", "raw_message": "Rs 999 BROADBAND BILL"},
    
    # Entertainment
    {"amount": 599.00, "merchant": "NETFLIX", "category": "Entertainment", "currency": "INR", "timestamp": "2026-02-01T00:00:00", "raw_message": "Rs 599 NETFLIX SUBSCRIPTION"},
    {"amount": 450.00, "merchant": "PVR CINEMAS", "category": "Entertainment", "currency": "INR", "timestamp": "2026-02-06T19:00:00", "raw_message": "Rs 450 at PVR CINEMAS"},
    {"amount": 199.00, "merchant": "SPOTIFY", "category": "Entertainment", "currency": "INR", "timestamp": "2026-02-02T00:00:00", "raw_message": "Rs 199 SPOTIFY PREMIUM"},
    {"amount": 350.00, "merchant": "BOOKMYSHOW", "category": "Entertainment", "currency": "INR", "timestamp": "2026-02-05T18:00:00", "raw_message": "Rs 350 BOOK MY SHOW"},
    
    # Groceries
    {"amount": 1580.00, "merchant": "DMART", "category": "Groceries", "currency": "INR", "timestamp": "2026-02-10T11:00:00", "raw_message": "Rs 1,580 at DMART"},
    {"amount": 2100.50, "merchant": "BIG BAZAAR", "category": "Groceries", "currency": "INR", "timestamp": "2026-02-08T15:00:00", "raw_message": "Rs 2,100.50 at BIG BAZAAR"},
    {"amount": 780.00, "merchant": "RELIANCE FRESH", "category": "Groceries", "currency": "INR", "timestamp": "2026-02-06T16:00:00", "raw_message": "Rs 780 at RELIANCE FRESH"},
    
    # Health
    {"amount": 650.00, "merchant": "APOLLO PHARMACY", "category": "Health", "currency": "INR", "timestamp": "2026-02-09T14:00:00", "raw_message": "Rs 650 at APOLLO PHARMACY"},
    {"amount": 1200.00, "merchant": "DR CLINIC", "category": "Health", "currency": "INR", "timestamp": "2026-02-07T10:00:00", "raw_message": "Rs 1,200 DR CONSULTATION"},
    
    # Income (Credits)
    {"amount": 45000.00, "merchant": "SALARY", "category": "Other", "currency": "INR", "timestamp": "2026-02-01T00:00:00", "raw_message": "Rs 45,000 salary credited"},
    {"amount": 5000.00, "merchant": "REFUND", "category": "Other", "currency": "INR", "timestamp": "2026-02-04T12:00:00", "raw_message": "Rs 5,000 credited - REFUND"},
    {"amount": 2500.00, "merchant": "CASHBACK", "category": "Other", "currency": "INR", "timestamp": "2026-02-03T15:00:00", "raw_message": "Rs 2,500 credited - CASHBACK"},
    
    # Other
    {"amount": 500.00, "merchant": "ATM", "category": "Other", "currency": "INR", "timestamp": "2026-02-05T20:00:00", "raw_message": "Rs 500 ATM WITHDRAWAL"},
]

print("=" * 70)
print("ADDING TRANSACTIONS DIRECTLY TO SUPABASE")
print("=" * 70)

try:
    # Insert all transactions at once
    result = supabase.table("transactions").insert(transactions).execute()
    
    print(f"\n[SUCCESS] Added {len(result.data)} transactions to Supabase!")
    print("\nSample transactions:")
    for i, txn in enumerate(result.data[:5], 1):
        print(f"  {i}. {txn['category']:20s} | Rs {txn['amount']:8.2f} | {txn['merchant']}")
    
    if len(result.data) > 5:
        print(f"  ... and {len(result.data) - 5} more")
    
    print("\n" + "=" * 70)
    print("[OK] Check your Supabase dashboard - all transactions are there!")
    print("[OK] Your Android app should now display all 30 transactions!")
    print("=" * 70)
    
except Exception as e:
    print(f"\n[ERROR] Failed to add transactions: {e}")
    print("\nTrying one-by-one method...")
    
    success = 0
    for txn in transactions:
        try:
            supabase.table("transactions").insert(txn).execute()
            success += 1
        except Exception as e2:
            print(f"  Failed: {txn['merchant']} - {e2}")
    
    print(f"\n[DONE] Added {success}/{len(transactions)} transactions")
