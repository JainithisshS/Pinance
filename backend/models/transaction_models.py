from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class ParseMessageRequest(BaseModel):
    raw_message: str = Field(..., description="Raw SMS/notification text")
    timestamp: Optional[datetime] = Field(
        default=None, description="When the message was received (optional)",
    )


class Transaction(BaseModel):
    id: Optional[int] = None
    amount: float
    merchant: Optional[str] = None
    category: Optional[str] = None
    currency: str = "INR"
    timestamp: datetime
    raw_message: str
