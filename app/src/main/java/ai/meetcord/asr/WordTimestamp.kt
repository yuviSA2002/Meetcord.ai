package ai.meetcord.asr

data class WordTimestamp(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val isFinal: Boolean,
    var speaker: String? = null
)
