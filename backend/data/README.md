# Category Dataset for SMS/Notifications

You can either create `transactions_labeled.csv` manually or auto-generate it.

## Format

- File: `transactions_labeled.csv`
- Columns:
  - `text` – full SMS/notification text (masked account/card numbers)
  - `category` – one of the fixed labels below

## Allowed Categories

- Food & Dining
- Transport
- Shopping
- Bills & Utilities
- Groceries
- Entertainment
- Health
- Education
- Investment
- Other

## Example rows

```csv
text,category
"Rs.292.30 Dr. from A/C XXXXXX9246 and Cr. to zomatofd.payu@hdfcbank. AvlBal:Rs679.59(...)",Food & Dining
"Rs.412.25 Dr. from A/C XXXXXX9246 and Cr. to cf.irctc@cashfreensdlpb. AvlBal:Rs479.40(...)",Transport
"Rs.371.25 Dr. from A/C XXXXXX9246 and Cr. to paytm.d19587502305@pty. AvlBal:Rs293.40(...)",Other
```

You can mix real messages (masking identifiers) and synthetic ones you write.

## Auto-generate synthetic dataset

Run this script from the `backend` folder to generate a dataset
with ~1,200 rows (about 120 per category):

```bash
python generate_synthetic_dataset.py
```

This will overwrite `transactions_labeled.csv` with synthetic data
that follows realistic Indian bank/SMS patterns.
