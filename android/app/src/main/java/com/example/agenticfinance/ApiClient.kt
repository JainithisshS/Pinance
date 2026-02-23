package com.example.agenticfinance

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // When running on Android emulator, this points to the host machine
    private const val BASE_URL = "http://10.12.227.48:8000"

    val api: FinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FinanceApi::class.java)
    }
}
