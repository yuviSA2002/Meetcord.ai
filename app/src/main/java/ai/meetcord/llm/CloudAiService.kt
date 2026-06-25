package ai.meetcord.llm

import ai.meetcord.settings.SettingsManager
import android.util.Log
import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class OpenAiRequest(
    val model: String = "gpt-4o",
    val messages: List<OpenAiMessage>,
    val temperature: Double = 0.7
)

data class OpenAiMessage(
    val role: String,
    val content: String
)

data class OpenAiResponse(
    val choices: List<OpenAiChoice>
)

data class OpenAiChoice(
    val message: OpenAiMessage
)

interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun generateCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiRequest
    ): OpenAiResponse
}

// Gemini Models
data class GeminiRequest(val contents: List<GeminiContent>)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiPart(val text: String)

data class GeminiResponse(val candidates: List<GeminiCandidate>?)
data class GeminiCandidate(val content: GeminiContent?)

interface GeminiApi {
    @POST
    suspend fun generateContent(@retrofit2.http.Url url: String, @Body request: GeminiRequest): GeminiResponse
}

// Claude Models
data class ClaudeRequest(
    val model: String = "claude-3-5-sonnet-20240620",
    val max_tokens: Int = 1000,
    val messages: List<ClaudeMessage>
)
data class ClaudeMessage(val role: String, val content: String)
data class ClaudeResponse(val content: List<ClaudeContent>?)
data class ClaudeContent(val text: String)

interface ClaudeApi {
    @POST("v1/messages")
    suspend fun generateMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: ClaudeRequest
    ): ClaudeResponse
}

object CloudAiService {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    private val openAiApi = retrofit.create(OpenAiApi::class.java)
    
    private val claudeRetrofit = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()
    private val claudeApi = claudeRetrofit.create(ClaudeApi::class.java)

    private val geminiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()
    private val geminiApi = geminiRetrofit.create(GeminiApi::class.java)

    suspend fun generateFinalSummary(transcript: String, modelName: String): String {
        val prompt = """
            You are an expert AI meeting assistant.
            Below is a full raw transcript of a meeting, complete with timestamps or slight errors from the Speech-to-Text engine.
            Your task is to analyze the entire meeting and output exactly TWO sections:
            
            1. MEETING SUMMARY:
            A comprehensive, well-structured multi-paragraph summary of the discussion.
            
            2. ACTION ITEMS:
            A bulleted list of specific tasks, who is responsible, and any deadlines mentioned. Format these as lines starting with "-".
            
            Meeting Transcript:
            $transcript
        """.trimIndent()

        return try {
            when (modelName) {
                "ChatGPT-4o" -> {
                    val key = SettingsManager.openAiKey.value
                    if (key.isBlank()) return "Error: OpenAI API Key is missing. Please configure it in Settings."
                    
                    val req = OpenAiRequest(
                        messages = listOf(
                            OpenAiMessage(role = "system", content = "You are an expert AI meeting assistant."),
                            OpenAiMessage(role = "user", content = prompt)
                        )
                    )
                    val response = openAiApi.generateCompletion("Bearer ${"$"}key", req)
                    response.choices.firstOrNull()?.message?.content ?: "Error: Empty response"
                }
                "Claude 3.5" -> {
                    val key = SettingsManager.anthropicKey.value
                    if (key.isBlank()) return "Error: Anthropic API Key is missing. Please configure it in Settings."
                    
                    val req = ClaudeRequest(
                        messages = listOf(
                            ClaudeMessage(role = "user", content = prompt)
                        )
                    )
                    val response = claudeApi.generateMessage(apiKey = key, request = req)
                    response.content?.firstOrNull()?.text ?: "Error: Empty response from Claude"
                }
                "Gemini Pro" -> {
                    val key = SettingsManager.geminiKey.value
                    if (key.isBlank()) return "Error: Gemini API Key is missing. Please configure it in Settings."
                    
                    val req = GeminiRequest(
                        contents = listOf(
                            GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                        )
                    )
                    val response = geminiApi.generateContent("v1beta/models/gemini-1.5-pro:generateContent?key=${key}", req)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Error: Empty response from Gemini"
                }
                else -> "Error: Unknown model selected."
            }
        } catch (e: Exception) {
            Log.e("CloudAiService", "Error generating summary", e)
            "Error generating summary: ${e.message}"
        }
    }
}
