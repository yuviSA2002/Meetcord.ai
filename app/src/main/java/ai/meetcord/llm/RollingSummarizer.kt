package ai.meetcord.llm

import ai.meetcord.asr.WhisperEngine
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object RollingSummarizer {
    private const val TAG = "RollingSummarizer"
    private const val BUFFER_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    private var currentSegmentText = StringBuilder()
    private var segmentStartTimeMs = -1L
    private var previousSummary = ""

    private val _summaryFlow = MutableStateFlow<List<String>>(emptyList())
    val summaryFlow: StateFlow<List<String>> = _summaryFlow

    private val summarizerScope = CoroutineScope(Dispatchers.IO + Job())
    private var summaryJob: Job? = null

    // Used to prevent concurrent LLM loading if timestamps are manipulated
    private val llmMutex = Mutex()

    fun startObserving() {
        summaryJob = summarizerScope.launch {
            WhisperEngine.wordsFlow.collect { words ->
                if (words.isNotEmpty()) {
                    val lastWord = words.last()
                    // We only accumulate final words to avoid duplicates from live predictions
                    if (lastWord.isFinal) {
                        if (segmentStartTimeMs == -1L) {
                            segmentStartTimeMs = lastWord.startMs
                        }
                        
                        currentSegmentText.append(lastWord.text).append(" ")

                        if ((lastWord.endMs - segmentStartTimeMs) >= BUFFER_DURATION_MS) {
                            val textToSummarize = currentSegmentText.toString()
                            
                            // Reset buffer
                            currentSegmentText.clear()
                            segmentStartTimeMs = lastWord.endMs
                            
                            launch {
                                triggerSummarizationSafe(textToSummarize)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun triggerSummarizationSafe(text: String) {
        llmMutex.withLock {
            Log.i(TAG, "Accumulated 10 minutes of text. Triggering Cloud LLM...")
            
            val prompt = """
                Summarize the following meeting segment and extract action items. 
                Maintain continuity with the previous summary.
                
                Previous Summary:
                ${if (previousSummary.isEmpty()) "None" else previousSummary}
                
                Meeting Segment:
                $text
            """.trimIndent()

            // Call Cloud API instead of local engine
            val model = ai.meetcord.llm.AiModelSelector.selectedModel.value
            val newSummary = CloudAiService.generateFinalSummary(prompt, model)
            
            previousSummary += "\n" + newSummary
            
            val updatedSummaries = _summaryFlow.value.toMutableList()
            updatedSummaries.add(newSummary)
            _summaryFlow.value = updatedSummaries
        }
    }

    fun stop() {
        summaryJob?.cancel()
    }
}
