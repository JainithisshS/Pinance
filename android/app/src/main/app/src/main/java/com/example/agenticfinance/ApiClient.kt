package com.example.agenticfinance

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // ── Toggle between local dev and HF Spaces cloud ──
    // Local emulator:  "http://10.0.2.2:8001"
    // Local device:    "http://<your-laptop-ip>:8001"
    // HF Spaces cloud: "https://jai005-pinance-backend.hf.space"
    private const val BASE_URL = "https://jai005-pinance-backend.hf.space"

    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            
            // Get Firebase ID token
            val user = FirebaseAuth.getInstance().currentUser
            val token = if (user != null) {
                try {
                    runBlocking {
                        user.getIdToken(false).await().token
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            
            // Add Authorization header if token exists
            val newRequest = if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            
            return chain.proceed(newRequest)
        }
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .build()

    val api: FinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FinanceApi::class.java)
    }
    
    val roadmapApi: LearningRoadmapApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LearningRoadmapApi::class.java)
    }
}
