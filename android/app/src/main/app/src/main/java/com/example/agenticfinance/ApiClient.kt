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
    // ── Toggle between local dev and Render cloud ──
    // Local emulator:  "http://10.0.2.2:8001"
    // Local device:    "http://<your-laptop-ip>:8001"
    // Render cloud:    "https://<your-app>.onrender.com"
    private const val BASE_URL = "http://10.0.2.2:8001"

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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
