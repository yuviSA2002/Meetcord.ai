package ai.meetcord.api

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CloudLLMClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = MediaType.parse("application/json; charset=utf-8")

    suspend fun generateSummaryAndDiarization(
        provider: String, // "OpenAI" or "Gemini"
        apiKey: String,
        prompt: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (provider == "OpenAI") {
                val jsonBody = JSONObject().apply {
                    put("model", "gpt-4o-mini") // Fast and cheap
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are an expert transcriber.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("response_format", JSONObject().apply { put("type", "json_object") })
                    put("temperature", 0.1)
                }

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(RequestBody.create(JSON, jsonBody.toString()))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body()?.string()
                
                if (response.isSuccessful && responseString != null) {
                    val json = JSONObject(responseString)
                    return@withContext json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else {
                    println("OpenAI Error: $responseString")
                }
            } else if (provider == "Gemini") {
                val jsonBody = JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.1)
                    })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                    .post(RequestBody.create(JSON, jsonBody.toString()))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body()?.string()
                
                if (response.isSuccessful && responseString != null) {
                    val json = JSONObject(responseString)
                    return@withContext json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                } else {
                    println("Gemini Error: $responseString")
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
