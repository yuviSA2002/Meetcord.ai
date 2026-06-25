package ai.meetcord.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AudioCaptureMode {
    MIC_ONLY,
    VOIP_CALLS,
    INTERNAL_MEDIA_ONLY,
    INTERNAL_MEDIA_AND_MIC
}

object SettingsManager {
    private const val PREFS_NAME = "meetcord_settings"
    private lateinit var prefs: SharedPreferences

    // Keys
    private const val KEY_OPENAI_API_KEY = "openai_api_key"
    private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_AUTO_SUMMARY = "auto_generate_summary"
    private const val KEY_AUDIO_CAPTURE_MODE = "audio_capture_mode"

    private val _openAiKey = MutableStateFlow("")
    val openAiKey: StateFlow<String> = _openAiKey

    private val _anthropicKey = MutableStateFlow("")
    val anthropicKey: StateFlow<String> = _anthropicKey

    private val _geminiKey = MutableStateFlow("")
    val geminiKey: StateFlow<String> = _geminiKey

    private val _autoGenerateSummary = MutableStateFlow(false)
    val autoGenerateSummary: StateFlow<Boolean> = _autoGenerateSummary

    private val _audioCaptureMode = MutableStateFlow(AudioCaptureMode.MIC_ONLY)
    val audioCaptureMode: StateFlow<AudioCaptureMode> = _audioCaptureMode

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _openAiKey.value = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        _anthropicKey.value = prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: ""
        _geminiKey.value = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        _autoGenerateSummary.value = prefs.getBoolean(KEY_AUTO_SUMMARY, false)
        
        val modeStr = prefs.getString(KEY_AUDIO_CAPTURE_MODE, AudioCaptureMode.MIC_ONLY.name)
        _audioCaptureMode.value = try {
            AudioCaptureMode.valueOf(modeStr ?: AudioCaptureMode.MIC_ONLY.name)
        } catch (e: Exception) {
            AudioCaptureMode.MIC_ONLY
        }
    }

    fun setOpenAiKey(key: String) {
        prefs.edit().putString(KEY_OPENAI_API_KEY, key).apply()
        _openAiKey.value = key
    }

    fun setAnthropicKey(key: String) {
        prefs.edit().putString(KEY_ANTHROPIC_API_KEY, key).apply()
        _anthropicKey.value = key
    }

    fun setGeminiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
        _geminiKey.value = key
    }

    fun setAutoGenerateSummary(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SUMMARY, enabled).apply()
        _autoGenerateSummary.value = enabled
    }

    fun setAudioCaptureMode(mode: AudioCaptureMode) {
        prefs.edit().putString(KEY_AUDIO_CAPTURE_MODE, mode.name).apply()
        _audioCaptureMode.value = mode
    }
}
