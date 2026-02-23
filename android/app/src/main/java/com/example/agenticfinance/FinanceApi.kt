package com.example.agenticfinance

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FinanceApi {

    @POST("/api/parse_message")
    suspend fun parseMessage(
        @Body body: ParseMessageRequestDto
    ): TransactionDto

    @POST("/api/synthesize")
    suspend fun synthesize(
        @Body body: SynthesizeRequestDto
    ): SynthesizeResponseDto

    @GET("/api/learning/next-card")
    suspend fun getNextCard(
        @Query("user_id") userId: String
    ): CardResponseDto

    @POST("/api/learning/submit-answer")
    suspend fun submitAnswer(
        @Body body: SubmitAnswerRequestDto
    ): SubmitAnswerResponseDto
}
