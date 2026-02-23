package com.example.agenticfinance

data class TransactionDto(
    val id: Int,
    val amount: Double,
    val merchant: String?,
    val category: String,
    val currency: String,
    val timestamp: String,
    val raw_message: String
)
