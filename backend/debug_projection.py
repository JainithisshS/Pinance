import requests
import json

# Test with debug output
response = requests.post(
    'http://127.0.0.1:8000/api/analyze_finance',
    json={'start_date': '2026-02-01', 'end_date': '2026-02-13'}
)

data = response.json()

# Calculate what the projection should be
days_in_range = 13
coverage = days_in_range / 30
actual_expenses = data['summary']['total_expenses']
projected_expenses = actual_expenses / coverage

print("=" * 70)
print("PROJECTION CALCULATION CHECK")
print("=" * 70)
print(f"Days in range: {days_in_range}")
print(f"Coverage ratio: {coverage:.1%}")
print(f"\nActual expenses (13 days): Rs {actual_expenses:,.2f}")
print(f"Projected monthly expenses: Rs {projected_expenses:,.2f}")
print(f"\nActual income (13 days): Rs {data['summary']['total_income']:,.2f}")
print(f"Projected monthly income: Rs {data['summary']['total_income'] / coverage:,.2f}")
print(f"\nProjected monthly savings: Rs {(data['summary']['total_income'] / coverage) - projected_expenses:,.2f}")
print(f"Projected savings rate: {((data['summary']['total_income'] / coverage) - projected_expenses) / (data['summary']['total_income'] / coverage):.1%}")
print("\n" + "=" * 70)
print("CURRENT ML ASSESSMENT")
print("=" * 70)
print(f"Risk: {data['ml_risk_level']}")
print(f"Explanation: {data['ml_risk_explanation']}")
print("\n" + "=" * 70)
print("EXPECTED: Risk should be based on projected ~Rs 57K spending,")
print("not the partial Rs 24K!")
print("=" * 70)
