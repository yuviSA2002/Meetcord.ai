package ai.meetcord

import ai.meetcord.asr.WhisperEngine
import ai.meetcord.audio.AudioCaptureService
import ai.meetcord.llm.RollingSummarizer
import ai.meetcord.ui.TranscriptionScreen
import ai.meetcord.ui.theme.Theme
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import ai.meetcord.ui.AppNavigation
import ai.meetcord.settings.SettingsManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            startTranscriptionPipeline()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(applicationContext)

        setContent {
            Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startTranscriptionPipeline()
        }
    }

    private fun startTranscriptionPipeline() {
        // 1. Initialize Whisper
        val modelPath = copyModelFromAssets()
        WhisperEngine.initialize(modelPath)
        
        // Audio processing is now handled centrally by AudioCaptureService
    }

    private fun copyModelFromAssets(): String {
        val modelFile = java.io.File(cacheDir, "ggml-base.en.bin")
        if (!modelFile.exists()) {
            assets.open("ggml-base.en.bin").use { inputStream ->
                java.io.FileOutputStream(modelFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return modelFile.absolutePath
    }

    override fun onDestroy() {
        super.onDestroy()
        WhisperEngine.release()
        val serviceIntent = Intent(this, AudioCaptureService::class.java)
        stopService(serviceIntent)
    }
}
