package com.example.agenticfinance

data class LearningCardDto(
    val id: String,
    val concept_id: String,
    val content: String,
    val quiz: QuizDto,
    val source: String,
    val created_at: String
)

data class QuizDto(
    val question: String,
    val options: List<String>,
    val correct_answer_index: Int,
    val explanation: String
)

data class ExplanationDto(
    val why_selected: String,
    val readiness: Double,
    val urgency: Double,
    val relevance: Double,
    val mastery_level: String,
    val interaction_count: Int
)

data class CardResponseDto(
    val card: LearningCardDto,
    val explanation: ExplanationDto
)

data class SubmitAnswerRequestDto(
    val card_id: String,
    val answer_index: Int,
    val time_spent_seconds: Int
)

data class SubmitAnswerResponseDto(
    val is_correct: Boolean,
    val explanation: String,
    val belief_update: BeliefUpdateDto,
    val next_card_ready: Boolean
)

data class BeliefUpdateDto(
    val concept_id: String,
    val previous_mastery: Double,
    val new_mastery: Double,
    val change: String,
    val mastery_level: String
)
