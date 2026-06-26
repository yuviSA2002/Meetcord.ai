package ai.meetcord.api

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CloudLLMClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()

    suspend fun generateSummaryAndDiarization(
        provider: String, // "OpenAI" or "Gemini"
        apiKey: String,
        prompt: String
    ): String? = withContext(Dispatchers.IO) {
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
                .post(jsonBody.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string()
            
            if (response.isSuccessful && responseString != null) {
                val json = JSONObject(responseString)
                return@withContext json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                throw RuntimeException("OpenAI HTTP Error: $responseString")
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
                    put("temperature", 0.1)
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string()
            
            if (response.isSuccessful && responseString != null) {
                val json = JSONObject(responseString)
                return@withContext json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            } else {
                try {
                    val modelsRequest = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                        .get()
                        .build()
                    val modelsResp = client.newCall(modelsRequest).execute()
                    val modelsStr = modelsResp.body?.string()
                    val modelsJson = JSONObject(modelsStr ?: "{}")
                    val modelsArr = modelsJson.optJSONArray("models")
                    val names = mutableListOf<String>()
                    if (modelsArr != null) {
                        for (i in 0 until modelsArr.length()) {
                            val m = modelsArr.getJSONObject(i)
                            if (m.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true) {
                                names.add(m.optString("name"))
                            }
                        }
                    }
                    throw RuntimeException("Gemini HTTP Error 404. Available models for your key: $names \nOriginal Error: $responseString")
                } catch (e: Exception) {
                    throw RuntimeException("Gemini HTTP Error: $responseString (Also failed to fetch models: ${e.message})")
                }
            }
        }
        throw RuntimeException("Unknown provider")
    }
}
