"""Add realistic dummy transaction data to Supabase for Android UI testing."""
import requests
from datetime import datetime, timedelta
import random

BASE_URL = "http://127.0.0.1:8000"

# Realistic transaction templates
dummy_transactions = [
    # Food & Dining
    {"raw_message": "Rs 450 debited from A/c XX1234 at SWIGGY on 10-Feb-26"},
    {"raw_message": "INR 320.50 spent at ZOMATO on your card"},
    {"raw_message": "Rs 180 debited at STARBUCKS COFFEE"},
    {"raw_message": "Debited: 250 at MCDONALDS"},
    {"raw_message": "Rs 890 spent at DOMINOS PIZZA"},
    
    # Shopping
    {"raw_message": "Rs 2,499 debited from card XX1234 at AMAZON on 09-Feb-26"},
    {"raw_message": "INR 1,850 spent at FLIPKART"},
    {"raw_message": "Rs 3,200.00 debited at MYNTRA"},
    {"raw_message": "Transaction of 1500 rupees completed at RELIANCE DIGITAL"},
    
    # Transport
    {"raw_message": "Rs 85 debited from A/c XX1234 by UPI/UBER"},
    {"raw_message": "UPI-Rs 120.00 debited-XX1234-OLA"},
    {"raw_message": "Rs 50 spent at METRO CARD RECHARGE"},
    {"raw_message": "INR 300 debited for PETROL PUMP"},
    
    # Bills & Utilities
    {"raw_message": "Rs 1,250 debited for ELECTRICITY BILL"},
    {"raw_message": "INR 850.00 spent at AIRTEL PREPAID"},
    {"raw_message": "Rs 599 debited for NETFLIX SUBSCRIPTION"},
    {"raw_message": "Debited: 999 for BROADBAND BILL"},
    
    # Groceries
    {"raw_message": "Rs 1,580 debited at DMART"},
    {"raw_message": "INR 2,100.50 spent at BIG BAZAAR"},
    {"raw_message": "Rs 780 debited from card at RELIANCE FRESH"},
    
    # Entertainment
    {"raw_message": "Rs 450 spent at PVR CINEMAS"},
    {"raw_message": "INR 199 debited for SPOTIFY PREMIUM"},
    {"raw_message": "Rs 350 spent at BOOK MY SHOW"},
    
    # Health
    {"raw_message": "Rs 650 debited at APOLLO PHARMACY"},
    {"raw_message": "INR 1,200 spent at DR CONSULTATION"},
    
    # Income (Credits)
    {"raw_message": "Rs 45,000 salary credited to your account"},
    {"raw_message": "INR 5,000 credited to A/c XX1234 - REFUND"},
    {"raw_message": "Rs 2,500 credited - CASHBACK"},
    
    # Other
    {"raw_message": "Rs 500 debited at ATM WITHDRAWAL"},
    {"raw_message": "INR 1,000 spent at GIFT CARD PURCHASE"},
]

print("=" * 70)
print("ADDING DUMMY TRANSACTIONS TO SUPABASE")
print("=" * 70)

success_count = 0
error_count = 0

for i, transaction in enumerate(dummy_transactions, 1):
    try:
        response = requests.post(
            f"{BASE_URL}/api/parse_message",
            json=transaction,
            timeout=5
        )
        
        if response.status_code == 200:
            data = response.json()
            success_count += 1
            print(f"\n{i}. [OK] Added: {data['category']:20s} | Rs {data['amount']:8.2f} | {data['merchant'] or 'N/A'}")
        else:
            error_count += 1
            print(f"\n{i}. [FAIL] Failed: {transaction['raw_message'][:50]}")
            
    except Exception as e:
        error_count += 1
        print(f"\n{i}. [ERROR] Error: {str(e)[:50]}")

print("\n" + "=" * 70)
print(f"SUMMARY: {success_count} added, {error_count} failed")
print("=" * 70)
print("\n[OK] Check your Supabase dashboard to see all transactions!")
print("[OK] Now test your Android app - it should display all this data!")
