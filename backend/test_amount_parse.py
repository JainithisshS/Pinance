import re

s = "JAINITHISSH S Ai Dear UPI user A/C X7794 debited by 70.00 on date 23Feb26 trf to BAVESH RAAM S Refno"
pattern = re.compile(r"(?:inr|rs\.?|rs\s*\.|₹)\s*([0-9,]+\.?[0-9]*)|([0-9,]+\.?[0-9]*)\s*rupees|(?:debited|credited|spent|amount|transaction)(?:\s*(?:by|for|of|:)?\s*)([0-9,]+\.?[0-9]*)", re.IGNORECASE)
match = pattern.search(s)
print('pattern match:', bool(match))
if match:
    print('groups:', match.groups())
kb = re.search(r"(?:debited|credited)\D{0,15}([0-9,]+\.?[0-9]*)", s, re.IGNORECASE)
print('kb match:', bool(kb))
if kb:
    print('kb group:', kb.group(1))
anynum = re.search(r"([0-9]{2,}[0-9,]*\.?[0-9]*)", s)
print('anynum match:', bool(anynum))
if anynum:
    print('anynum:', anynum.group(1))
