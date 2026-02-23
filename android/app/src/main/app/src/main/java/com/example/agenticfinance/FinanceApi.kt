package com.example.agenticfinance

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FinanceApi {

    @POST("/api/parse_message")
    suspend fun parseMessage(
        @Body body: ParseMessageRequestDto
    ): TransactionDto

    @GET("/api/transactions")
    suspend fun getTransactions(): List<TransactionDto>

    @POST("/api/analyze_finance")
    suspend fun analyzeFinance(
        @Body body: FinanceAnalysisRequestDto
    ): FinanceAnalysisResponseDto

    @POST("/api/expense_breakdown")
    suspend fun expenseBreakdown(
        @Body body: FinanceAnalysisRequestDto
    ): List<ExpenseCategorySummaryDto>

    @POST("/api/analyze_news")
    suspend fun analyzeNews(
        @Body body: NewsAnalysisRequestDto
    ): NewsAnalysisResponseDto

    @GET("/api/news_feed")
    suspend fun getNewsFeed(): List<NewsFeedArticleDto>

    @GET("/api/market_index")
    suspend fun getMarketIndex(): List<MarketIndexDto>

    @GET("/api/sector_indices")
    suspend fun getSectorIndices(): List<SectorIndexDto>

    @GET("/api/market_data")
    suspend fun getMarketData(): MarketDataDto

    @POST("/api/analyze_article")
    suspend fun analyzeArticle(
        @Body body: ArticleAnalysisRequestDto
    ): ArticleAnalysisResponseDto

    @POST("/api/synthesize")
    suspend fun synthesize(
        @Body body: RecommendationRequestDto
    ): RecommendationResponseDto

    @GET("/api/curriculum/plan")
    suspend fun getCurriculumPlan(): CurriculumPlanResponseDto

    @POST("/api/curriculum/update")
    suspend fun updateCurriculum(
        @Body body: CurriculumUpdateRequestDto
    ): CurriculumPlanResponseDto

    @POST("/api/synthesize")
    suspend fun synthesizeChat(
        @Body body: SynthesizeChatRequestDto
    ): RecommendationResponseDto
    @GET("/api/learning/next-card")
    suspend fun getNextCard(
        @retrofit2.http.Query("exclude") exclude: String = ""
    ): CardResponseDto

    @POST("/api/learning/submit-answer")
    suspend fun submitAnswer(
        @Body body: SubmitAnswerRequestDto
    ): SubmitAnswerResponseDto
}

data class BeliefStateDto(
    val unknown: Double,
    val partial: Double,
    val mastered: Double
)

data class PlanItemDto(
    val concept_id: String,
    val concept_name: String,
    val action: String,
    val reason: String,
    val priority: Double,
    val content_snippet: String,
    val card_title: String = "",
    val learning_text: String = "",
    val quiz_question: String = "",
    val quiz_options: List<String> = emptyList(),
    val quiz_correct: Int = 0
)

data class CurriculumPlanResponseDto(
    val plan: List<PlanItemDto>,
    val beliefs: Map<String, BeliefStateDto>,
    val compiler_log: List<String>
)

data class ObservationModelDto(
    val concept_id: String,
    val observation: String
)

data class CurriculumUpdateRequestDto(
    val beliefs: Map<String, BeliefStateDto>,
    val observation: ObservationModelDto
)

data class TransactionDto(
    val amount: Double,
    val merchant: String?,
    val category: String?,
    val timestamp: String
)

data class FinanceAnalysisRequestDto(
    val start_date: String,
    val end_date: String
)

data class TransactionSummaryDto(
    val total_spent: Double,
    val transactions_count: Int,
    val top_category: String?,
    val start_date: String,
    val end_date: String,
    val total_income: Double,
    val total_expenses: Double,
    val savings: Double
)

data class FinanceAnalysisResponseDto(
    val summary: TransactionSummaryDto,
    val risk_level: String,
    val message: String,
    val ml_risk_level: String? = null,
    val ml_risk_confidence: Double? = null,
    val ml_risk_explanation: String? = null,
    val ml_confidence_band: String? = null
)

data class ExpenseCategorySummaryDto(
    val category: String,
    val total_spent: Double,
    val transactions_count: Int
)

data class NewsAnalysisRequestDto(
    val topic: String
)

data class NewsArticleDto(
    val title: String,
    val source: String,
    val url: String?
)

data class NewsSentimentBreakdownDto(
    val positive: Int,
    val negative: Int,
    val neutral: Int
)

data class NewsAnalysisResponseDto(
    val topic: String,
    val overall_sentiment: String,
    val sentiment_breakdown: NewsSentimentBreakdownDto,
    val sample_articles: List<NewsArticleDto>,
    val summary: String,
    val trend_label: String? = null,
    val trend_confidence: Double? = null,
    val dominant_sector: String? = null,
    val llm_summary: String? = null
)

data class NewsFeedArticleDto(
    val id: Int,
    val title: String,
    val source: String,
    val summary: String,
    val url: String?,
    val image_url: String?
)

data class ArticleAnalysisRequestDto(
    val title: String,
    val summary: String,
    val url: String?
)

data class ArticleAnalysisResponseDto(
    val company: String,
    val sector: String,
    val trend: String,
    val recommendation: String,
    val reasoning: String,
    val confidence_score: Double,
    val confidence_level: String,
    val confidence_explanation: String,
    val impact_strength: String
)

data class RecommendationRequestDto(
    val finance_insight: String,
    val news_insight: String
)

data class RecommendationResponseDto(
    val recommendation: String,
    val rationale: String,
    val retrieved_knowledge: List<String>
)

data class ChatTurnDto(
    val role: String,
    val content: String
)

data class SynthesizeChatRequestDto(
    val finance_insight: String,
    val news_insight: String,
    val user_question: String?,
    val history: List<ChatTurnDto>
)

// Adaptive Learning DTOs are defined in LearningModels.kt

data class MarketIndexDto(
    val name: String,
    val price: Double,
    val change: Double,
    val change_percent: Double,
    val source: String,
    val timestamp: String? = null
)

data class SectorIndexDto(
    val name: String,
    val display_name: String,
    val price: Double,
    val change: Double,
    val change_percent: Double
)

data class MarketDataDto(
    val indices: List<MarketIndexDto>,
    val sectors: List<SectorIndexDto>
)


