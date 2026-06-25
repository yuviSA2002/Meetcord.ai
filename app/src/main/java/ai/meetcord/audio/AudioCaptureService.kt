package ai.meetcord.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import ai.meetcord.asr.WordTimestamp
import ai.meetcord.asr.WhisperEngine
class AudioCaptureService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var audioRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaProjection: android.media.projection.MediaProjection? = null

    companion object {
        const val CHANNEL_ID = "MeetcordAudioChannel"
        const val NOTIFICATION_ID = 101

        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        var lastSavedWavPath: String? = null
    }

    private var randomAccessFile: java.io.RandomAccessFile? = null
    private var totalAudioLen: Long = 0

    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val finalizedWords = mutableListOf<WordTimestamp>()
    private var globalStartTimeMs = 0L
    private var lastChunkEndMs = 0L
    private var isServiceRunning = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            val captureMode = ai.meetcord.settings.SettingsManager.audioCaptureMode.value
            if (captureMode == ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_ONLY || captureMode == ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_AND_MIC) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val audioFile = java.io.File(getExternalFilesDir(null), "meeting_${System.currentTimeMillis()}.wav")
        lastSavedWavPath = audioFile.absolutePath
        
        // Open file and write a dummy 44-byte WAV header
        randomAccessFile = java.io.RandomAccessFile(audioFile, "rw")
        writeWavHeader(randomAccessFile!!, 0, 0, SAMPLE_RATE.toLong(), 1, (SAMPLE_RATE * 16 * 1 / 8).toLong())
        totalAudioLen = 0

        startAudioCapture(intent)

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture(intent: Intent?) {
        globalStartTimeMs = System.currentTimeMillis()
        lastChunkEndMs = 0L
        finalizedWords.clear()
        
        // Initialize Whisper locally for true 100% accurate offline real-time transcription
        val modelPath = java.io.File(cacheDir, "ggml-base.en.bin").absolutePath
        ai.meetcord.asr.WhisperEngine.initialize(modelPath)
        ai.meetcord.asr.WhisperEngine.clear()

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, AudioBufferPool.BUFFER_SIZE * 2)

        val captureMode = ai.meetcord.settings.SettingsManager.audioCaptureMode.value

        if (captureMode == ai.meetcord.settings.AudioCaptureMode.MIC_ONLY) {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
        } else if (captureMode == ai.meetcord.settings.AudioCaptureMode.VOIP_CALLS) {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
        } else if (captureMode == ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_ONLY || captureMode == ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_AND_MIC) {
            val resultCode = intent?.getIntExtra("projection_result", 0) ?: 0
            val projectionData = intent?.getParcelableExtra<Intent>("projection_data")
            if (resultCode != 0 && projectionData != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, projectionData)
                if (mediaProjection != null) {
                    val config = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                        .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                        .build()
                        
                    internalRecord = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(android.media.AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build())
                        .setBufferSizeInBytes(bufferSize)
                        .build()
                }
            }
            if (captureMode == ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_AND_MIC) {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            }
        }

        audioRecord?.startRecording()
        internalRecord?.startRecording()

        serviceScope.launch {
            while (isActive) {
                val isMicRecording = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
                val isIntRecording = internalRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
                
                if (!isMicRecording && !isIntRecording) break

                val mixedBuffer = AudioBufferPool.obtain()
                var readResult = 0

                if (audioRecord != null && internalRecord != null) {
                    val micBuffer = ShortArray(mixedBuffer.size)
                    val intBuffer = ShortArray(mixedBuffer.size)
                    
                    val micRead = audioRecord?.read(micBuffer, 0, micBuffer.size) ?: 0
                    val intRead = internalRecord?.read(intBuffer, 0, intBuffer.size) ?: 0
                    
                    readResult = maxOf(micRead, intRead)
                    
                    for (i in 0 until readResult) {
                        val micSample = if (i < micRead) micBuffer[i] else 0
                        val intSample = if (i < intRead) intBuffer[i] else 0
                        
                        val mixed = (micSample + intSample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        mixedBuffer[i] = mixed.toShort()
                    }
                } else if (audioRecord != null) {
                    readResult = audioRecord?.read(mixedBuffer, 0, mixedBuffer.size) ?: 0
                } else if (internalRecord != null) {
                    readResult = internalRecord?.read(mixedBuffer, 0, mixedBuffer.size) ?: 0
                }

                if (readResult > 0) {
                    if (RecordingManager.state.value == RecordingState.RECORDING) {
                        var sumSq = 0.0
                        for (i in 0 until readResult) {
                            val sample = mixedBuffer[i].toDouble()
                            sumSq += sample * sample
                        }

                        // Save to disk
                        try {
                            val byteBuffer = java.nio.ByteBuffer.allocate(readResult * 2)
                            byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            byteBuffer.asShortBuffer().put(mixedBuffer, 0, readResult)
                            val bytes = byteBuffer.array()
                            randomAccessFile?.write(bytes)
                            totalAudioLen += bytes.size
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        val rms = Math.sqrt(sumSq / readResult.coerceAtLeast(1))
                        val normalizedRms = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        AudioRepository.emitAmplitude(normalizedRms)
                        
                        AudioRepository.emitAudioChunk(mixedBuffer)

                        // Push a copy of the buffer directly to the Whisper C++ engine
                        val whisperChunk = AudioBufferPool.obtain()
                        for (i in 0 until readResult) {
                            whisperChunk[i] = mixedBuffer[i]
                        }
                        WhisperEngine.pushAudio(whisperChunk, readResult)
                    } else {
                        AudioBufferPool.recycle(mixedBuffer)
                    }
                } else {
                    AudioBufferPool.recycle(mixedBuffer)
                }
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        // PARTIAL_WAKE_LOCK keeps CPU running when the screen is off.
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Meetcord::AudioCaptureWakeLock"
        ).apply {
            acquire() // Strict requirement: Microphone stays hot when screen is off
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meetcord.ai is Listening")
            .setContentText("Transcribing your meeting securely.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Default icon, change later
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }



    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meeting Transcription",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the transcription engine running in the background."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        
        // Flush Whisper with trailing silence to ensure final words are extracted before releasing
        CoroutineScope(Dispatchers.IO).launch {
            for (i in 0 until 40) {
                val silentChunk = AudioBufferPool.obtain()
                for (j in 0 until silentChunk.size) silentChunk[j] = 0
                WhisperEngine.pushAudio(silentChunk, silentChunk.size)
                kotlinx.coroutines.delay(10)
            }
            kotlinx.coroutines.delay(1000)
            WhisperEngine.release()
        }
        
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        
        internalRecord?.apply {
            stop()
            release()
        }
        internalRecord = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        releaseWakeLock()
        
        // Update WAV header with accurate sizes
        randomAccessFile?.let { raf ->
            try {
                val totalDataLen = totalAudioLen + 36
                val byteRate = (SAMPLE_RATE * 16 * 1 / 8).toLong()
                writeWavHeader(raf, totalAudioLen, totalDataLen, SAMPLE_RATE.toLong(), 1, byteRate)
                raf.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        randomAccessFile = null
    }

    private fun writeWavHeader(out: java.io.RandomAccessFile, totalAudioLen: Long, totalDataLen: Long, longSampleRate: Long, channels: Int, byteRate: Long) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = channels.toByte(); header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte(); header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte(); header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte(); header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte(); header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.seek(0)
        out.write(header, 0, 44)
        out.seek(out.length()) // Move back to the end if appending more
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Unbound service, runs independently
    }
}
