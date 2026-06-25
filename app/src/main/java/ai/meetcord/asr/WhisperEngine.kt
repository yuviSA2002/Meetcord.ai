package ai.meetcord.asr

import ai.meetcord.audio.AudioBufferPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * JNI Bridge for whisper.cpp
 * Manages memory boundaries explicitly to prevent memory leaks.
 */
object WhisperEngine {
    
    init {
        System.loadLibrary("meetcord_jni")
    }

    private var contextPtr: Long = 0L

    private val _wordsFlow = MutableStateFlow<List<WordTimestamp>>(emptyList())
    val wordsFlow: StateFlow<List<WordTimestamp>> = _wordsFlow

    // Native methods
    private external fun initModelNative(modelPath: String): Long
    private external fun freeModelNative(contextPtr: Long)
    private external fun processAudioChunkNative(contextPtr: Long, chunk: ShortArray, length: Int)
    private external fun transcribeFileNative(contextPtr: Long, filePath: String)

    fun initialize(modelPath: String) {
        if (contextPtr == 0L) {
            contextPtr = initModelNative(modelPath)
        }
    }

    fun release() {
        if (contextPtr != 0L) {
            freeModelNative(contextPtr)
            contextPtr = 0L
        }
    }

    fun clear() {
        _wordsFlow.value = emptyList()
    }

    fun pushAudio(chunk: ShortArray, length: Int) {
        if (contextPtr != 0L) {
            // Push data to C++ background thread
            processAudioChunkNative(contextPtr, chunk, length)
        }
        // Explicit memory management: recycle buffer after JNI copy is complete
        AudioBufferPool.recycle(chunk)
    }

    fun transcribeFile(filePath: String) {
        if (contextPtr != 0L) {
            transcribeFileNative(contextPtr, filePath)
        }
    }

    /**
     * Called synchronously by the C++ background worker when a new token is extracted.
     */
    @JvmStatic
    fun onNewWordExtracted(word: String, startMs: Long, endMs: Long, isFinal: Boolean) {
        val currentList = _wordsFlow.value.toMutableList()
        currentList.add(WordTimestamp(word, startMs, endMs, isFinal))
        _wordsFlow.value = currentList
    }

    /**
     * Override the entire list (used by Android SpeechRecognizer)
     */
    fun setWords(words: List<WordTimestamp>) {
        _wordsFlow.value = words
    }
}
