package com.twinmind.recorder.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

data class WhisperResponse(val text: String)

interface WhisperApiService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") format: RequestBody
    ): WhisperResponse
}

data class GeminiContent(val parts: List<GeminiPart>, val role: String = "user")
data class GeminiPart(val text: String)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig = GenerationConfig()
)
data class GenerationConfig(
    val temperature: Float = 0.4f,
    val maxOutputTokens: Int = 2048
)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)
data class GeminiCandidate(val content: GeminiContent?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @Streaming
    @POST("v1beta/models/gemini-2.5-flash:streamGenerateContent")
    suspend fun streamContent(
        @Query("key") apiKey: String,
        @Query("alt") alt: String,
        @Body request: GeminiRequest
    ): Response<ResponseBody>
}
