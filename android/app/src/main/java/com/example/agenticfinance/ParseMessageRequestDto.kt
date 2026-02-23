package com.example.agenticfinance

data class ParseMessageRequestDto(
    val raw_message: String,
    val timestamp: String? = null
)
