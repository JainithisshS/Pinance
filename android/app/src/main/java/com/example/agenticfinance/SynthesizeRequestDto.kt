package com.example.agenticfinance

data class SynthesizeRequestDto(
    val finance_insight: String,
    val news_insight: String,
    val user_question: String?,
    val history: List<ChatTurnDto>
)
