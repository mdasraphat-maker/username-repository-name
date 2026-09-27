package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiEndpoint {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val api: GeminiApiEndpoint by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiEndpoint::class.java)
    }

    private const val SYSTEM_PROMPT =
        "তোমার নাম মায়া (Maya)। তুমি ব্যবহারকারীর অত্যন্ত মিষ্টি, কেয়ারিং, বিনয়ী এবং বুদ্ধিমান ব্যক্তিগত এআই সহকারী। " +
        "তুমি সবসময় প্রাকটিক্যাল, সংক্ষিপ্ত ও মিষ্টি সুরে বাংলায় উত্তর দিবে (যদি ব্যবহারকারী ইংরেজিতে প্রশ্ন করে তবে ইংরেজিতেও সুন্দর উত্তর দিতে পারো)। " +
        "তুমি পরিবারের একজন আপনজনের মতো সহানুভূতিশীল।"

    suspend fun askMaya(
        userPrompt: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = if (!customApiKey.isNullOrBlank()) {
            customApiKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "No valid Gemini API key configured.")
            return@withContext Result.failure(IllegalStateException("API_KEY_MISSING"))
        }

        try {
            val contentsList = mutableListOf<Content>()
            // Include up to 4 recent conversation turns
            val recentTurns = conversationHistory.takeLast(4)
            for ((role, text) in recentTurns) {
                contentsList.add(
                    Content(
                        role = if (role == "USER") "user" else "model",
                        parts = listOf(Part(text = text))
                    )
                )
            }
            contentsList.add(
                Content(
                    role = "user",
                    parts = listOf(Part(text = userPrompt))
                )
            )

            val request = GeminiRequest(
                contents = contentsList,
                systemInstruction = Content(
                    parts = listOf(Part(text = SYSTEM_PROMPT))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    topK = 40,
                    topP = 0.95f
                )
            )

            val response = api.generateContent(key, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                Result.success(text.trim())
            } else {
                Result.failure(Exception("খালি উত্তর পাওয়া গেছে"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
