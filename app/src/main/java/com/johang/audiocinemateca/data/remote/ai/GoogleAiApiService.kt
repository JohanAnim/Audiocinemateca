package com.johang.audiocinemateca.data.remote.ai

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GoogleAiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GoogleAiRequest
    ): Response<GoogleAiResponse>

    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent?alt=sse")
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GoogleAiRequest
    ): Response<ResponseBody>
}

data class GoogleAiRequest(
    @SerializedName("contents") val contents: List<Content>,
    @SerializedName("systemInstruction") val systemInstruction: Content? = null,
    @SerializedName("tools") val tools: List<Tool>? = null,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig? = null
)

data class Content(
    @SerializedName("role") val role: String? = null,
    @SerializedName("parts") val parts: List<Part>
)

data class Part(
    @SerializedName("text") val text: String? = null,
    @SerializedName("thought") val thought: Boolean? = null,
    @SerializedName("thoughtSignature") val thoughtSignature: String? = null,
    @SerializedName("functionCall") val functionCall: FunctionCall? = null,
    @SerializedName("functionResponse") val functionResponse: FunctionResponse? = null
)

data class FunctionCall(
    @SerializedName("name") val name: String,
    @SerializedName("id") val id: String? = null,
    @SerializedName("args") val args: Map<String, Any>? = null
)

data class FunctionResponse(
    @SerializedName("name") val name: String,
    @SerializedName("id") val id: String? = null,
    @SerializedName("response") val response: Map<String, Any>
)

data class Tool(
    @SerializedName("functionDeclarations") val functionDeclarations: List<FunctionDeclaration>
)

data class FunctionDeclaration(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("parameters") val parameters: Parameters
)

data class Parameters(
    @SerializedName("type") val type: String = "object",
    @SerializedName("properties") val properties: Map<String, Property>,
    @SerializedName("required") val required: List<String>
)

data class Property(
    @SerializedName("type") val type: String,
    @SerializedName("description") val description: String
)

data class GenerationConfig(
    @SerializedName("temperature") val temperature: Float? = null,
    @SerializedName("topP") val topP: Float? = null,
    @SerializedName("topK") val topK: Int? = null,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int? = null,
    @SerializedName("thinkingConfig") val thinkingConfig: ThinkingConfig? = null
)

data class ThinkingConfig(
    @SerializedName("includeThoughts") val includeThoughts: Boolean? = null,
    @SerializedName("thinkingLevel") val thinkingLevel: String? = null,
    @SerializedName("thinkingBudget") val thinkingBudget: Int? = null
)

data class GoogleAiResponse(
    @SerializedName("candidates") val candidates: List<Candidate>? = null,
    @SerializedName("error") val error: RemoteError? = null
)

data class Candidate(
    @SerializedName("content") val content: Content? = null,
    @SerializedName("finishReason") val finishReason: String? = null
)

data class RemoteError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("status") val status: String
)
