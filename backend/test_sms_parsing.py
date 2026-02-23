"""Test SMS parsing with real-world Indian bank message formats."""

import re
from typing import Optional

# Current regex from transactions.py
AMOUNT_PATTERN = re.compile(r"(?i)(?:inr|rs\.?|rs\s*\.)\s*([0-9,]+\.?[0-9]*)")

def parse_amount(message: str) -> Optional[float]:
    match = AMOUNT_PATTERN.search(message)
    if not match:
        return None
    value = match.group(1).replace(",", "")
    try:
        return float(value)
    except ValueError:
        return None

# Real-world SMS formats from Indian banks
test_messages = [
    # HDFC Bank
    "INR 500.00 spent at Swiggy on your card",
    "Rs 1,250.50 debited from A/c XX1234 on 12-Feb-26",
    "Your A/c XX1234 is debited with Rs.2500.00 on 12-FEB-26",
    
    # ICICI Bank
    "Rs.750 debited from card XX1234 at AMAZON on 12-Feb",
    "Your Card XX1234 used for Rs 3,500.00 at FLIPKART",
    
    # SBI
    "Dear Customer, Rs 450.00 has been debited from your account",
    "A/c XX1234 debited by Rs.1200 on 12-Feb-26",
    
    # Axis Bank
    "Rs 2,500.50 spent on Axis Bank Card XX1234",
    
    # UPI transactions
    "Rs 300 debited from A/c XX1234 by UPI/PAYTM",
    "UPI-Rs 150.00 debited-XX1234-GOOGLEPAY",
    
    # Credit transactions
    "Rs 5,000.00 credited to A/c XX1234 on 12-Feb",
    "INR 10000 salary credited to your account",
    
    # Edge cases
    "Amount Rs. 99.99 debited",
    "Spent Rs.50 at local store",
    "Transaction of 500 rupees completed",  # No Rs/INR prefix
    "Debited: 1000",  # No currency symbol
]

print("=" * 80)
print("SMS PARSING TEST RESULTS")
print("=" * 80)

failed_count = 0
for i, msg in enumerate(test_messages, 1):
    amount = parse_amount(msg)
    status = "[PASS]" if amount is not None and amount > 0 else "[FAIL]"
    
    if amount is None or amount == 0:
        failed_count += 1
    
    print(f"\n{i}. {status}")
    print(f"   Message: {msg}")
    print(f"   Parsed:  {amount}")

print("\n" + "=" * 80)
print(f"SUMMARY: {len(test_messages) - failed_count}/{len(test_messages)} passed, {failed_count} failed")
print("=" * 80)

# Test improved regex patterns
print("\n\nTESTING IMPROVED REGEX PATTERNS:")
print("=" * 80)

# Improved pattern that handles more cases
IMPROVED_PATTERN = re.compile(
    r"(?i)(?:inr|rs\.?|rs\s*\.|₹)\s*([0-9,]+\.?[0-9]*)|"  # Standard Rs/INR/₹
    r"(?i)(?:amount|debited|credited|spent|transaction)\s*:?\s*(?:of\s+)?(?:rs\.?|inr|₹)?\s*([0-9,]+\.?[0-9]*)"  # After keywords
)

def parse_amount_improved(message: str) -> Optional[float]:
    match = IMPROVED_PATTERN.search(message)
    if not match:
        return None
    # Check both capture groups
    value = match.group(1) or match.group(2)
    if value:
        value = value.replace(",", "")
        try:
            return float(value)
        except ValueError:
            return None
    return None

failed_improved = 0
for i, msg in enumerate(test_messages, 1):
    amount = parse_amount_improved(msg)
    status = "[PASS]" if amount is not None and amount > 0 else "[FAIL]"
    
    if amount is None or amount == 0:
        failed_improved += 1
    
    print(f"\n{i}. {status}")
    print(f"   Message: {msg}")
    print(f"   Parsed:  {amount}")

print("\n" + "=" * 80)
print(f"IMPROVED: {len(test_messages) - failed_improved}/{len(test_messages)} passed, {failed_improved} failed")
print("=" * 80)
