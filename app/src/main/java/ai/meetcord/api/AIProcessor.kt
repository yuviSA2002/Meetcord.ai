package ai.meetcord.api

import ai.meetcord.asr.WordTimestamp
import org.json.JSONObject

object AIProcessor {

    suspend fun processMeetingWithAI(
        provider: String,
        apiKey: String,
        rawTranscript: String,
        wordTimestamps: List<WordTimestamp>
    ): ProcessResult? {
        // 1. Pre-chunk transcript by pauses/sentences
        data class Chunk(val id: Int, val words: List<WordTimestamp>)
        val chunks = mutableListOf<Chunk>()
        var currentWords = mutableListOf<WordTimestamp>()
        
        for (i in wordTimestamps.indices) {
            val word = wordTimestamps[i]
            currentWords.add(word)
            
            val nextWord = wordTimestamps.getOrNull(i + 1)
            val timeDiff = if (nextWord != null) nextWord.startMs - word.endMs else 0
            val isPunctuation = word.text.trim().matches(Regex(".*[.?!]$"))
            
            if (timeDiff > 800 || isPunctuation || currentWords.size >= 25 || nextWord == null) {
                chunks.add(Chunk(chunks.size, currentWords))
                currentWords = mutableListOf()
            }
        }

        val numberedTranscript = chunks.joinToString("\n") { chunk ->
            "[${chunk.id}] " + chunk.words.joinToString(" ") { it.text }
        }

        val prompt = """
            You are an expert AI meeting assistant.
            Below is a meeting transcript that has been split into numbered segments like [0], [1], etc.
            
            Your tasks:
            1. Write a brief summary of the overall meeting.
            2. Extract a list of actionable to-do items. Format this strictly as markdown checkboxes: '- [ ] Task Name'.
            3. Identify the speakers for each segment based on the conversational flow (e.g., "Speaker 1", "Speaker 2").
            
            You MUST return a strictly valid JSON object in this exact format. Do NOT return markdown blocks, ONLY the raw JSON string:
            {
              "summary": "Meeting summary here...",
              "actionItems": "- [ ] Item 1\n- [ ] Item 2",
              "speakerMapping": [
                {"id": 0, "speaker": "Speaker 1"},
                {"id": 1, "speaker": "Speaker 1"},
                {"id": 2, "speaker": "Speaker 2"}
              ]
            }
            
            Numbered Transcript:
            $numberedTranscript
        """.trimIndent()

        val jsonString = CloudLLMClient.generateSummaryAndDiarization(provider, apiKey, prompt) ?: throw RuntimeException("Empty response from LLM")
            // Clean markdown if LLM accidentally included it
            val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()
            val resultObj = JSONObject(cleanJson)
            
            val summary = resultObj.getString("summary")
            val actionItems = resultObj.getString("actionItems")
            val speakerMapping = resultObj.getJSONArray("speakerMapping")
            
            // Map IDs back to Speakers securely
            val idToSpeaker = mutableMapOf<Int, String>()
            for (i in 0 until speakerMapping.length()) {
                val mapping = speakerMapping.getJSONObject(i)
                idToSpeaker[mapping.getInt("id")] = mapping.getString("speaker")
            }
            
            for (chunk in chunks) {
                val speaker = idToSpeaker[chunk.id] ?: "Unknown Speaker"
                chunk.words.forEach { it.speaker = speaker }
            }
            
            return ProcessResult(summary, actionItems, wordTimestamps)
    }
}

data class ProcessResult(
    val summary: String,
    val actionItems: String,
    val alignedTimestamps: List<WordTimestamp>
)
