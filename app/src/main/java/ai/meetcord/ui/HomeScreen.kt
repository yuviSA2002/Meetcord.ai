package ai.meetcord.ui

import ai.meetcord.audio.AudioCaptureService
import ai.meetcord.audio.RecordingManager
import ai.meetcord.audio.RecordingState
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val recordingState by RecordingManager.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isRecording = recordingState == RecordingState.RECORDING

    // Background Gradient Animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
                    start = androidx.compose.ui.geometry.Offset(0f, gradientOffset),
                    end = androidx.compose.ui.geometry.Offset(1000f, gradientOffset + 1000f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Meetcord.ai",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (recordingState) {
                    RecordingState.IDLE -> "Ready to record"
                    RecordingState.RECORDING -> "Listening..."
                    RecordingState.PAUSED -> "Paused"
                },
                color = Color(0xFFCCCCCC),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // AI Model Selector removed from HomeScreen

            // Waveform takes up the middle space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                WaveformVisualizer(isRecording = isRecording)
            }

            // Massive glowing button
            val recordingScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "recordingScale"
            )
            
            val scale = if (isRecording) recordingScale else 1f

            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulseAlpha"
            )

            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulseScale"
            )

            // Layout for buttons
            var showSaveDialog by remember { mutableStateOf(false) }
            
            fun triggerStopAndSave() {
                showSaveDialog = true
                RecordingManager.pauseRecording()
            }
            
            val leftOffset by animateDpAsState(
                targetValue = if (recordingState != RecordingState.IDLE) (-60).dp else 0.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "leftOffset"
            )
            
            val rightOffset by animateDpAsState(
                targetValue = if (recordingState != RecordingState.IDLE) 60.dp else 0.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "rightOffset"
            )

            val stopAlpha by animateFloatAsState(
                targetValue = if (recordingState != RecordingState.IDLE) 1f else 0f,
                animationSpec = tween(500),
                label = "stopAlpha"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            ) {
                // STOP Button (Moves Right)
                if (stopAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .offset(x = rightOffset)
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFFEF4444), Color(0xFF991B1B))))
                            .clickable { triggerStopAndSave() }
                            .alpha(stopAlpha),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Recording",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Main Play/Pause Button (Moves Left)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.offset(x = leftOffset)
                ) {
                    if (isRecording) {
                        // Pulsing aura ring 1
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFFE11D48).copy(alpha = pulseAlpha))
                        )
                        
                        // Pulsing aura ring 2
                        val pulseScale2 by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.8f,
                            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "pulse2"
                        )
                        val pulseAlpha2 by infiniteTransition.animateFloat(
                            initialValue = 0.3f, targetValue = 0f,
                            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "alpha2"
                        )
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .scale(pulseScale2)
                                .clip(CircleShape)
                                .background(Color(0xFFFFFFFF).copy(alpha = pulseAlpha2))
                        )
                    }

                    val buttonColor by animateColorAsState(
                        targetValue = if (isRecording) Color(0xFFE11D48) else Color(0xFFFFFFFF),
                        label = "btnColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(buttonColor, buttonColor.copy(alpha = 0.5f))
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                when (recordingState) {
                                    RecordingState.IDLE -> {
                                        val captureMode = ai.meetcord.settings.SettingsManager.audioCaptureMode.value
                                            if (captureMode == ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_ONLY || captureMode == ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_AND_MIC) {
                                                val intent = Intent(context, ai.meetcord.audio.TransparentProjectionActivity::class.java)
                                                context.startActivity(intent)
                                            } else {
                                                val intent = Intent(context, AudioCaptureService::class.java)
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    context.startForegroundService(intent)
                                                } else {
                                                    context.startService(intent)
                                                }
                                                ai.meetcord.asr.WhisperEngine.clear()
                                                RecordingManager.startRecording()
                                            }
                                    }
                                    RecordingState.RECORDING -> RecordingManager.pauseRecording()
                                    RecordingState.PAUSED -> RecordingManager.startRecording()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (recordingState) {
                                RecordingState.IDLE -> Icons.Default.PlayArrow
                                RecordingState.RECORDING -> Icons.Default.Pause
                                RecordingState.PAUSED -> Icons.Default.PlayArrow
                            },
                            contentDescription = "Toggle Recording",
                            tint = if (isRecording) Color.White else Color.Black,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            if (showSaveDialog) {
                var meetingName by remember { mutableStateOf("Meeting ${System.currentTimeMillis()}") }
                val db = ai.meetcord.data.MeetingDatabase.getDatabase(context)

                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Save Meeting", color = Color.White) },
                    text = {
                        OutlinedTextField(
                            value = meetingName,
                            onValueChange = { meetingName = it },
                            label = { Text("Meeting Name") },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            showSaveDialog = false
                            RecordingManager.stopRecording()
                            val intent = Intent(context, AudioCaptureService::class.java)
                            context.stopService(intent)

                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val transcript = ai.meetcord.asr.WhisperEngine.wordsFlow.value.joinToString(" ") { it.text }
                                
                                val entity = ai.meetcord.data.MeetingEntity(
                                    name = meetingName,
                                    timestampMs = System.currentTimeMillis(),
                                    audioFilePath = ai.meetcord.audio.AudioCaptureService.lastSavedWavPath ?: "",
                                    transcriptJson = com.google.gson.Gson().toJson(ai.meetcord.asr.WhisperEngine.wordsFlow.value),
                                    summary = "",
                                    actionItems = "",
                                    aiModelUsed = ""
                                )
                                val newId = db.meetingDao().insertMeeting(entity)
                                
                                if (ai.meetcord.settings.SettingsManager.autoGenerateSummary.value) {
                                    val provider = if (ai.meetcord.settings.SettingsManager.openAiKey.value.isNotBlank()) "OpenAI" else "Gemini"
                                    val key = if (provider == "OpenAI") ai.meetcord.settings.SettingsManager.openAiKey.value else ai.meetcord.settings.SettingsManager.geminiKey.value
                                    
                                    if (key.isNotBlank()) {
                                        val result = ai.meetcord.api.AIProcessor.processMeetingWithAI(provider, key, transcript, ai.meetcord.asr.WhisperEngine.wordsFlow.value)
                                        if (result != null) {
                                            val updated = entity.copy(
                                                id = newId.toInt(),
                                                summary = result.summary,
                                                actionItems = result.actionItems,
                                                transcriptJson = com.google.gson.Gson().toJson(result.alignedTimestamps),
                                                aiModelUsed = provider
                                            )
                                            db.meetingDao().updateMeeting(updated)
                                        }
                                    }
                                }
                            }
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showSaveDialog = false 
                            RecordingManager.stopRecording()
                            val intent = Intent(context, AudioCaptureService::class.java)
                            context.stopService(intent)
                        }) {
                            Text("Discard", color = Color.Red)
                        }
                    },
                    containerColor = Color(0xFF111111)
                )
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
            }
        )
    }
}

@Composable
fun WaveformVisualizer(isRecording: Boolean) {
    var globalPhase by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    val currentAmp = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0.01f) }

    // Phase animation loop
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var lastTime = androidx.compose.runtime.withFrameNanos { it }
        while (isActive) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - lastTime) / 1e9f
            lastTime = now
            globalPhase += 4f * dt // Speed of the wave
        }
    }

    // Amplitude animation loop
    androidx.compose.runtime.LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive) {
                val micAmp = ai.meetcord.audio.AudioRepository.amplitudeFlow.value
                val targetAmp = (micAmp * 25f).coerceIn(0.05f, 1f) // Scale for Siri Wave
                currentAmp.animateTo(targetAmp, animationSpec = androidx.compose.animation.core.tween(50))
                kotlinx.coroutines.delay(50)
            }
        } else {
            currentAmp.animateTo(0.01f, animationSpec = androidx.compose.animation.core.tween(500))
        }
    }

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp)) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        val curves = listOf(
            Triple(1.0f, 2.0f, Color(0xFF1CB5E0)), // Cyan
            Triple(0.8f, 2.0f, Color(0xFFFF007F)), // Magenta
            Triple(0.6f, 2.0f, Color(0xFF0000FF)), // Blue
            Triple(0.4f, 2.0f, Color(0xFF00FF00)), // Green
            Triple(1.0f, 3.0f, Color(0xFFFFFFFF)) // Main center line
        )

        for ((index, curveConfig) in curves.withIndex()) {
            val (attenuationK, strokeW, curveColor) = curveConfig
            val path = androidx.compose.ui.graphics.Path()

            val K = 2.0f // Frequency
            val phaseShift = globalPhase * (if (index % 2 == 0) -1f else 1f) * (0.5f + index * 0.2f)

            path.moveTo(0f, midY)
            var xPos = 0f
            val step = 3f
            while (xPos <= w) {
                val xNorm = (xPos / w) * 2f - 1f // -1 to 1
                val attenuation = (1.5f / (1f + Math.pow(xNorm.toDouble(), 4.0))).toFloat() * attenuationK
                
                val yVal = currentAmp.value * attenuation * kotlin.math.sin(K * xNorm * Math.PI - globalPhase + phaseShift)
                val yPixel = midY - (yVal.toFloat() * h / 2f)

                path.lineTo(xPos, yPixel)
                xPos += step
            }

            drawIntoCanvas { canvas ->
                // Outer Glow Paint
                val glowPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = (strokeW * 3).dp.toPx()
                    color = curveColor.copy(alpha = 0.5f).toArgb()
                    maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                }
                
                // Solid Core Paint
                val corePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokeW.dp.toPx()
                    color = curveColor.toArgb()
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                }

                canvas.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint)
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), corePaint)
            }
        }
    }
}
