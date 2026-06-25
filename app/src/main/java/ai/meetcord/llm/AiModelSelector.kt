package ai.meetcord.llm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AiModelSelector {
    val availableModels = listOf("ChatGPT-4o", "Claude 3.5", "Gemini Pro")
    
    private val _selectedModel = MutableStateFlow(availableModels[0]) // Default to ChatGPT
    val selectedModel: StateFlow<String> = _selectedModel

    fun selectModel(model: String) {
        if (availableModels.contains(model)) {
            _selectedModel.value = model
        }
    }
}
