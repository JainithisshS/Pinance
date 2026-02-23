"""Direct test of the updated transactions.py regex."""
import sys
sys.path.insert(0, r"c:\Users\jaini\OneDrive\Desktop\SEM-6\Software Engg\agentic-finance-system")

from backend.routers.transactions import _parse_amount

# Test cases that were failing
test_cases = [
    ("Transaction of 500 rupees completed", 500.0),
    ("Debited: 1000", 1000.0),
    ("Rs 500 spent", 500.0),  # Should still work
    ("INR 1,250.50 debited", 1250.5),  # Should still work
]

print("Testing updated _parse_amount function:")
print("=" * 60)

all_passed = True
for msg, expected in test_cases:
    result = _parse_amount(msg)
    status = "[PASS]" if result == expected else "[FAIL]"
    if result != expected:
        all_passed = False
    print(f"\n{status}")
    print(f"  Message:  {msg}")
    print(f"  Expected: {expected}")
    print(f"  Got:      {result}")

print("\n" + "=" * 60)
if all_passed:
    print("SUCCESS: All tests passed!")
else:
    print("FAILURE: Some tests failed")
