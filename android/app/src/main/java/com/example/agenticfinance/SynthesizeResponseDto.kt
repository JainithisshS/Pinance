package com.example.agenticfinance

data class SynthesizeResponseDto(
    val recommendation: String,
    val rationale: String,
    val retrieved_knowledge: List<String>
)
