package ai.meetcord.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Repository to act as a bridge between the Foreground Service (Producer)
 * and the JNI ASR pipeline (Consumer).
 */
object AudioRepository {
    
    // We emit the references to the pooled buffers.
    // The consumer MUST call AudioBufferPool.recycle() after use.
    private val _audioFlow = MutableSharedFlow<ShortArray>(extraBufferCapacity = 64)
    val audioFlow: SharedFlow<ShortArray> = _audioFlow

    private val _amplitudeFlow = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val amplitudeFlow: kotlinx.coroutines.flow.StateFlow<Float> = _amplitudeFlow

    suspend fun emitAudioChunk(chunk: ShortArray) {
        _audioFlow.emit(chunk)
    }

    fun emitAmplitude(amplitude: Float) {
        _amplitudeFlow.value = amplitude
    }
}
