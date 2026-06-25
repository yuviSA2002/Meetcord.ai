package ai.meetcord.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED
}

object RecordingManager {
    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state

    fun startRecording() {
        _state.value = RecordingState.RECORDING
    }

    fun pauseRecording() {
        _state.value = RecordingState.PAUSED
    }

    fun stopRecording() {
        _state.value = RecordingState.IDLE
    }
}
