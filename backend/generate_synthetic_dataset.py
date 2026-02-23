import random
from pathlib import Path

import pandas as pd


BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data"
OUT_PATH = DATA_DIR / "transactions_labeled.csv"


CATEGORY_DATA = {
    # Food & Dining
    "Food & Dining": {
        "brands": [
            "ZOMATO",
            "SWIGGY",
            "SWIGGY INSTAMART",
            "DOMINOS",
            "PIZZA HUT",
            "KFC",
            "MCDONALDS",
            "BURGER KING",
            "SUBWAY",
            "STARBUCKS",
            "CAFE COFFEE DAY",
            "FAASOS",
            "EATSURE",
            "FRESHMENU",
            "BEHROUZ BIRYANI",
            "OVEN STORY",
            "BOX8",
            "WOW MOMO",
            "CHAI POINT",
            "HALDIRAMS",
            "A2B",
            "SARAVANA BHAVAN",
        ],
        "common": [
            "RESTAURANT",
            "FOOD ORDER",
            "HOTEL",
            "CAFE",
            "ONLINE FOOD",
            "POS FOOD",
            "MEAL PAYMENT",
            "DINING",
        ],
    },
    # Transport
    "Transport": {
        "brands": [
            "OLA",
            "UBER",
            "RAPIDO",
            "REDBUS",
            "IRCTC",
            "INDIAN RAILWAYS",
            "METRO RAIL",
            "FASTAG",
            "NHAI FASTAG",
            "HP PETROL",
            "INDIAN OIL",
            "BHARAT PETROLEUM",
            "SHELL",
            "NAYARA ENERGY",
            "VOGO",
            "BOUNCE",
            "YULU",
            "PARKING CHARGES",
            "TOLL PLAZA",
        ],
        "common": [
            "CAB PAYMENT",
            "TRANSPORT",
            "TOLL PAYMENT",
            "PETROL BUNK",
            "METRO",
            "TRAVEL",
            "POS TRANSPORT",
        ],
    },
    # Shopping
    "Shopping": {
        "brands": [
            "AMAZON",
            "FLIPKART",
            "MYNTRA",
            "AJIO",
            "MEESHO",
            "SNAPDEAL",
            "TATA CLIQ",
            "RELIANCE TRENDS",
            "PANTALOONS",
            "LIFESTYLE",
            "WESTSIDE",
            "ZUDIO",
            "CROMA",
            "RELIANCE DIGITAL",
            "VIJAY SALES",
            "DECATHLON",
            "IKEA",
            "FIRSTCRY",
        ],
        "common": [
            "ONLINE SHOPPING",
            "ECOM PURCHASE",
            "POS SHOP",
            "MERCHANT STORE",
            "RETAIL SHOP",
        ],
    },
    # Bills & Utilities
    "Bills & Utilities": {
        "brands": [
            "TNEB",
            "BESCOM",
            "KSEB",
            "MSEB",
            "WATER BOARD",
            "GAS BILL",
            "LPG",
            "INDANE",
            "HP GAS",
            "BHARAT GAS",
            "JIO",
            "AIRTEL",
            "VI",
            "BSNL",
            "ACT FIBERNET",
            "HATHWAY",
            "TATA PLAY",
            "DISH TV",
            "SUN DIRECT",
            "ELECTRICITY RECHARGE",
        ],
        "common": [
            "UTILITY BILL",
            "MOBILE RECHARGE",
            "ONLINE RECHARGE",
            "ELECTRICITY BILL",
            "DTH RECHARGE",
        ],
    },
    # Groceries
    "Groceries": {
        "brands": [
            "BIGBASKET",
            "BIGBASKET DAILY",
            "JIOMART",
            "DMART",
            "RELIANCE FRESH",
            "RELIANCE SMART",
            "SPENCERS",
            "MORE SUPERMARKET",
            "STAR BAZAAR",
            "NILGIRIS",
            "SPAR",
            "HERITAGE FRESH",
            "LOCAL KIRANA STORE",
        ],
        "common": [
            "GROCERY STORE",
            "SUPERMARKET",
            "LOCAL STORE",
            "POS GROCERY",
        ],
    },
    # Entertainment
    "Entertainment": {
        "brands": [
            "NETFLIX",
            "AMAZON PRIME VIDEO",
            "DISNEY HOTSTAR",
            "SPOTIFY",
            "YOUTUBE PREMIUM",
            "BOOKMYSHOW",
            "PVR CINEMAS",
            "INOX",
            "SUN NXT",
            "ZEE5",
            "SONY LIV",
            "GAANA",
            "WYNK MUSIC",
            "MX PLAYER",
        ],
        "common": [
            "MOVIE TICKET",
            "ENTERTAINMENT",
            "STREAMING",
            "SUBSCRIPTION",
            "EVENT BOOKING",
        ],
    },
    # Health
    "Health": {
        "brands": [
            "APOLLO HOSPITALS",
            "FORTIS",
            "MANIPAL HOSPITALS",
            "AIIMS",
            "GOVERNMENT HOSPITAL",
            "MEDPLUS",
            "APOLLO PHARMACY",
            "1MG",
            "PHARMEASY",
            "NETMEDS",
            "PRACTO",
            "CLINIC VISIT",
            "DIAGNOSTIC LAB",
            "BLOOD TEST",
            "SCAN CENTER",
        ],
        "common": [
            "HOSPITAL PAYMENT",
            "MEDICAL STORE",
            "DOCTOR CONSULT",
            "LAB TEST",
            "HEALTHCARE",
        ],
    },
    # Education
    "Education": {
        "brands": [
            "COURSERA",
            "UDEMY",
            "EDX",
            "BYJUS",
            "UNACADEMY",
            "VEDANTU",
            "TOPPR",
            "SCALER",
            "GREAT LEARNING",
            "UPGRAD",
            "COLLEGE FEES",
            "SCHOOL FEES",
            "EXAM FEES",
            "ONLINE COURSE",
            "CERTIFICATION FEE",
        ],
        "common": [
            "EDUCATION PAYMENT",
            "COURSE FEE",
            "TUITION FEES",
            "LEARNING PLATFORM",
        ],
    },
    # Investment
    "Investment": {
        "brands": [
            "ZERODHA",
            "GROWW",
            "UPSTOX",
            "ANGEL ONE",
            "PAYTM MONEY",
            "COIN BY ZERODHA",
            "KUVERA",
            "ETMONEY",
            "MUTUAL FUND SIP",
            "STOCK BUY",
            "STOCK SELL",
            "IPO APPLICATION",
            "NPS CONTRIBUTION",
            "PPF DEPOSIT",
        ],
        "common": [
            "INVESTMENT",
            "STOCK MARKET",
            "SIP PAYMENT",
            "TRADING",
            "FINANCIAL INVESTMENT",
        ],
    },
    # Other
    "Other": {
        "brands": [
            "UPI TRANSFER",
            "FRIEND TRANSFER",
            "FAMILY TRANSFER",
            "SELF TRANSFER",
            "CASH WITHDRAWAL",
            "ATM WITHDRAWAL",
            "BANK CHARGES",
            "UPI CASHBACK",
            "REFUND",
            "REVERSAL",
            "UNKNOWN MERCHANT",
        ],
        "common": [
            "PAYMENT",
            "ONLINE TRANSFER",
            "POS TRANSACTION",
            "MISC",
        ],
    },
}


def generate_sms(merchant: str) -> str:
    amt = round(random.uniform(20, 5000), 2)
    ref = random.randint(10**11, 10**12 - 1)
    bal = round(random.uniform(100, 50000), 2)
    return (
        f"Rs.{amt} Dr. from A/C XXXXXXXX and Cr. to {merchant}. "
        f"Ref:{ref}. AvlBal:Rs{bal}."
    )


def generate_dataset(n: int = 20000) -> None:
    data = []
    per_cat = n // len(CATEGORY_DATA)

    for category, items in CATEGORY_DATA.items():
        merchants = items["brands"] + items["common"]
        for _ in range(per_cat):
            m = random.choice(merchants)
            sms = generate_sms(m)
            data.append([sms, category])

    random.shuffle(data)

    df = pd.DataFrame(data, columns=["text", "category"])
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    df.to_csv(OUT_PATH, index=False)
    print(df.shape)
    print(f"Saved dataset to {OUT_PATH}")


def main() -> None:
    generate_dataset(20000)


if __name__ == "__main__":
    main()
