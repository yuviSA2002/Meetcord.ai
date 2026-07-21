package ai.meetcord.ui

import ai.meetcord.asr.WordTimestamp
import ai.meetcord.data.MeetingDatabase
import ai.meetcord.data.MeetingEntity
import android.media.MediaPlayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TranscriptLine(val text: String, val startMs: Long, val endMs: Long, val speaker: String? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MeetingDetailScreen(meetingId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = MeetingDatabase.getDatabase(context)
    var meeting by remember { mutableStateOf<MeetingEntity?>(null) }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentPosition by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var isFormatError by remember { mutableStateOf(false) }
    var isGeneratingAI by remember { mutableStateOf(false) }
    var isRetranscribing by remember { mutableStateOf(false) }

    val openAiKey by ai.meetcord.settings.SettingsManager.openAiKey.collectAsState()
    val geminiKey by ai.meetcord.settings.SettingsManager.geminiKey.collectAsState()
    val anthropicKey by ai.meetcord.settings.SettingsManager.anthropicKey.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(meetingId) {
        scope.launch(Dispatchers.IO) {
            val fetchedMeeting = db.meetingDao().getMeetingById(meetingId)
            withContext(Dispatchers.Main) {
                meeting = fetchedMeeting
                if (fetchedMeeting != null) {
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(fetchedMeeting.audioFilePath)
                            prepare()
                            duration = this.duration
                            setOnCompletionListener { 
                                isPlaying = false
                                currentPosition = 0 
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isFormatError = true
                    }
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer?.currentPosition ?: 0
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    val retranscribedWords by ai.meetcord.asr.WhisperEngine.wordsFlow.collectAsState()
    LaunchedEffect(retranscribedWords) {
        if (isRetranscribing && retranscribedWords.isNotEmpty()) {
            // Debounce the database write by 2 seconds. 
            // If 2 seconds pass without a new word, we assume transcription is finished.
            delay(2000)
            
            isRetranscribing = false
            withContext(Dispatchers.IO) {
                val json = Gson().toJson(retranscribedWords)
                val updatedMeeting = meeting!!.copy(transcriptJson = json)
                db.meetingDao().updateMeeting(updatedMeeting)
                withContext(Dispatchers.Main) {
                    meeting = updatedMeeting
                }
            }
        }
    }

    if (meeting == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000)), contentAlignment = Alignment.Center) {
            // Modern Pulsing Loading State
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "loadingAlpha"
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFFFFFFF).copy(alpha = alpha))
            )
        }
        return
    }

    val lines = remember(meeting!!.transcriptJson, retranscribedWords, isRetranscribing) {
        try {
            val words = if (isRetranscribing && retranscribedWords.isNotEmpty()) {
                retranscribedWords
            } else {
                if (meeting!!.transcriptJson.isBlank() || meeting!!.transcriptJson == "[]") {
                    emptyList()
                } else {
                    Gson().fromJson(meeting!!.transcriptJson, Array<WordTimestamp>::class.java).toList()
                }
            }
            
            val result = mutableListOf<TranscriptLine>()
            var currentLine = ""
            var lineStart = 0L
            var currentSpeaker: String? = null
            for (word in words) {
                if (currentLine.isEmpty()) {
                    lineStart = word.startMs
                    currentSpeaker = word.speaker
                } else if (currentSpeaker != word.speaker && word.speaker != null) {
                    // Break line immediately if speaker changes
                    result.add(TranscriptLine(currentLine.trim(), lineStart, word.startMs, currentSpeaker))
                    currentLine = ""
                    lineStart = word.startMs
                    currentSpeaker = word.speaker
                }
                
                currentLine += word.text + " "
                if (word.text.trim().endsWith(".") || word.text.trim().endsWith("?") || currentLine.length > 60) {
                    result.add(TranscriptLine(currentLine.trim(), lineStart, word.endMs, currentSpeaker))
                    currentLine = ""
                }
            }
            if (currentLine.isNotEmpty() && words.isNotEmpty()) result.add(TranscriptLine(currentLine.trim(), lineStart, words.last().endMs, currentSpeaker))
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(meeting!!.name, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Meeting", tint = Color(0xFFEF4444))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF000000))
                )
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color(0xFF000000),
                    contentColor = Color(0xFFFFFFFF),
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = Color(0xFFFFFFFF)
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Playback & Lyrics", color = if (pagerState.currentPage == 0) Color.White else Color.Gray) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("AI Summary", color = if (pagerState.currentPage == 1) Color.White else Color.Gray) }
                    )
                }
            }
        },
        containerColor = Color(0xFF000000)
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            when (page) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (isFormatError) {
                            Text(
                                "This meeting uses an older audio format (.pcm) which is incompatible with the new media player. Please view the raw JSON on the next tab or start a new recording.",
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            // Lyrics LazyColumn
                            val listState = rememberLazyListState()
                            val currentIndex = lines.indexOfLast { it.startMs <= currentPosition }
                            
                            LaunchedEffect(currentIndex) {
                                if (currentIndex >= 0 && isPlaying) {
                                    listState.animateScrollToItem(maxOf(0, currentIndex - 2))
                                }
                            }

                            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                                itemsIndexed(lines) { index, line ->
                                    val isActive = index == currentIndex
                                    val showSpeakerLabel = line.speaker != null && (index == 0 || lines[index - 1].speaker != line.speaker)
                                    
                                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                        if (showSpeakerLabel) {
                                            Text(
                                                text = line.speaker ?: "Speaker",
                                                color = Color(0xFFFFFFFF),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = line.text,
                                            color = if (isActive) Color.White else Color.Gray,
                                            fontSize = if (isActive) 26.sp else 18.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            lineHeight = if (isActive) 34.sp else 26.sp,
                                            modifier = Modifier
                                                .clickable {
                                                    mediaPlayer?.seekTo(line.startMs.toInt())
                                                    currentPosition = line.startMs.toInt()
                                                    if (!isPlaying) {
                                                        mediaPlayer?.start()
                                                        isPlaying = true
                                                    }
                                                }
                                        )
                                    }
                                }
                                item {
                                    if (isRetranscribing) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            BrainBulbLoader(text = "Deep AI Offline Transcription running...")
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Tokens Extracted: ${retranscribedWords.size}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (lines.isEmpty()) {
                                                Text("No transcript available.", color = Color.Gray)
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }
                                            Button(
                                                onClick = {
                                                    isRetranscribing = true
                                                    scope.launch(Dispatchers.IO) {
                                                        try {
                                                            // CRITICAL FIX: Release and re-initialize the engine to clear the C++ KV Cache!
                                                            // Without this, feeding a massive new audio file crashes whisper.cpp via SIGABRT.
                                                            ai.meetcord.asr.WhisperEngine.release()
                                                            val modelPath = java.io.File(context.cacheDir, ai.meetcord.BuildConfig.WHISPER_MODEL).absolutePath
                                                            ai.meetcord.asr.WhisperEngine.initialize(modelPath)
                                                            ai.meetcord.asr.WhisperEngine.clear()

                                                            val file = File(meeting!!.audioFilePath)
                                                            if (file.exists()) {
                                                                ai.meetcord.asr.WhisperEngine.transcribeFile(file.absolutePath)
                                                            }
                                                        } catch (e: Throwable) {
                                                            e.printStackTrace()
                                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                                android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                                                isRetranscribing = false
                                                            }
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1CB5E0))
                                            ) {
                                                Text("Re-transcribe Audio with Deep AI", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Spotify-like Player Controls
                            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF111111), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(16.dp)) {
                                Slider(
                                    value = currentPosition.toFloat(),
                                    onValueChange = { 
                                        currentPosition = it.toInt()
                                        mediaPlayer?.seekTo(it.toInt())
                                    },
                                    valueRange = 0f..maxOf(1f, duration.toFloat()),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFFFFF),
                                        activeTrackColor = Color(0xFFFFFFFF),
                                        inactiveTrackColor = Color.Gray
                                    )
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { 
                                        mediaPlayer?.seekTo(maxOf(0, currentPosition - 10000))
                                        currentPosition = mediaPlayer?.currentPosition ?: 0
                                    }) {
                                        Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color(0xFFFFFFFF))
                                            .clickable {
                                                if (isPlaying) {
                                                    mediaPlayer?.pause()
                                                } else {
                                                    mediaPlayer?.start()
                                                }
                                                isPlaying = !isPlaying
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.Black,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }

                                    IconButton(onClick = { 
                                        mediaPlayer?.seekTo(minOf(duration, currentPosition + 10000))
                                        currentPosition = mediaPlayer?.currentPosition ?: 0
                                    }) {
                                        Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Summary Tab
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            Text("AI Summary & Insights", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (meeting!!.summary.isBlank()) {
                                if (isGeneratingAI) {
                                    BrainBulbLoader()
                                } else {
                                    // AI Model Selector Carousel
                                    val selectedModel by ai.meetcord.llm.AiModelSelector.selectedModel.collectAsState()
                                    androidx.compose.foundation.lazy.LazyRow(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        items(ai.meetcord.llm.AiModelSelector.availableModels.size) { index ->
                                            val model = ai.meetcord.llm.AiModelSelector.availableModels[index]
                                            val isSelected = model == selectedModel
                                            androidx.compose.material3.Card(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .clickable { ai.meetcord.llm.AiModelSelector.selectModel(model) },
                                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                                    containerColor = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF222222)
                                                ),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                            ) {
                                                Text(
                                                    text = model,
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            isGeneratingAI = true
                                            scope.launch {
                                                try {
                                                    val selModel = ai.meetcord.llm.AiModelSelector.selectedModel.value
                                                    val provider = when (selModel) {
                                                        "ChatGPT-4o" -> "OpenAI"
                                                        "Claude 3.5" -> "Anthropic"
                                                        "Gemini Pro" -> "Gemini"
                                                        else -> "Gemini"
                                                    }
                                                    val key = when (provider) {
                                                        "OpenAI" -> openAiKey
                                                        "Anthropic" -> anthropicKey
                                                        "Gemini" -> geminiKey
                                                        else -> geminiKey
                                                    }
                                                    if (key.isBlank()) {
                                                        withContext(Dispatchers.Main) {
                                                            android.widget.Toast.makeText(context, "$provider API Key not found! Please add it in Settings.", android.widget.Toast.LENGTH_SHORT).show()
                                                            isGeneratingAI = false
                                                        }
                                                        return@launch
                                                    }
                                                    
                                                    // Reconstruct raw transcript from Words safely
                                                    if (meeting!!.transcriptJson.isBlank() || meeting!!.transcriptJson == "[]") {
                                                            withContext(Dispatchers.Main) {
                                                                android.widget.Toast.makeText(context, "No transcript available to summarize.", android.widget.Toast.LENGTH_SHORT).show()
                                                                isGeneratingAI = false
                                                            }
                                                            return@launch
                                                        }
                                                        
                                                        val wordsArray = Gson().fromJson(meeting!!.transcriptJson, Array<WordTimestamp>::class.java)
                                                        if (wordsArray == null || wordsArray.isEmpty()) {
                                                            withContext(Dispatchers.Main) {
                                                                android.widget.Toast.makeText(context, "Transcript is empty.", android.widget.Toast.LENGTH_SHORT).show()
                                                                isGeneratingAI = false
                                                            }
                                                            return@launch
                                                        }
                                                        val words = wordsArray.toList()
                                                        val rawText = words.joinToString(" ") { it.text }
                                                        
                                                        val result = ai.meetcord.api.AIProcessor.processMeetingWithAI(provider, key, rawText, words)
                                                        
                                                        if (result != null) {
                                                            val updatedMeeting = meeting!!.copy(
                                                                summary = result.summary,
                                                                actionItems = result.actionItems,
                                                                transcriptJson = Gson().toJson(result.alignedTimestamps),
                                                                aiModelUsed = provider
                                                            )
                                                            db.meetingDao().updateMeeting(updatedMeeting)
                                                            meeting = updatedMeeting
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                android.widget.Toast.makeText(context, "AI Processing failed. Check your API key.", android.widget.Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    } catch (e: Throwable) {
                                                        android.util.Log.e("MEETCORD_FATAL", "Full Exception:", e)
                                                        e.printStackTrace()
                                                        withContext(Dispatchers.Main) {
                                                            android.widget.Toast.makeText(context, "Fatal Error: ${e.javaClass.simpleName} - ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    } finally {
                                                        isGeneratingAI = false
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFFFF))
                                        ) {
                                            Text("Generate Summary & Speakers", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                }
                            } else {
                                // Display Summary
                                Text("Summary", color = Color(0xFFFFFFFF), fontWeight = FontWeight.SemiBold)
                                Text(meeting!!.summary, color = Color(0xFFFFFFFF), fontSize = 16.sp, lineHeight = 24.sp)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Action Items", color = Color(0xFFFFFFFF), fontWeight = FontWeight.SemiBold)
                                
                                val actionItemsText = meeting!!.actionItems
                                val lines = actionItemsText.split("\n")
                                Column {
                                    for ((index, line) in lines.withIndex()) {
                                        val isChecked = line.trimStart().startsWith("- [x]", ignoreCase = true)
                                        val isUnchecked = line.trimStart().startsWith("- [ ]")
                                        if (isChecked || isUnchecked) {
                                            val textContent = line.replace(Regex("- \\[x\\]|- \\[ \\]", RegexOption.IGNORE_CASE), "").trim()
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically, 
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                                    val newLines = lines.toMutableList()
                                                    newLines[index] = if (isChecked) "- [ ] $textContent" else "- [x] $textContent"
                                                    val updatedMeeting = meeting!!.copy(actionItems = newLines.joinToString("\n"))
                                                    meeting = updatedMeeting
                                                    scope.launch(Dispatchers.IO) { db.meetingDao().updateMeeting(updatedMeeting) }
                                                }
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.Gray, checkmarkColor = Color.Black)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(textContent, color = Color.White, fontSize = 16.sp)
                                            }
                                        } else {
                                            Text(line, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Model Used: ${meeting!!.aiModelUsed}", color = Color.Gray, fontSize = 12.sp)

                                Spacer(modifier = Modifier.height(24.dp))
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(
                                        onClick = {
                                            val updatedMeeting = meeting!!.copy(summary = "", actionItems = "", aiModelUsed = "")
                                            meeting = updatedMeeting
                                            scope.launch(Dispatchers.IO) {
                                                db.meetingDao().updateMeeting(updatedMeeting)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1CB5E0))
                                    ) {
                                        Text("Retry Summary Generation", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                        item {
                            Text("Raw Transcript JSON (Debug)", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(meeting!!.transcriptJson, color = Color.DarkGray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Meeting?", color = Color.White) },
                text = { Text("Are you sure you want to delete this meeting? The audio recording and transcript will be permanently lost.", color = Color(0xFFFFFFFF)) },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val file = File(meeting!!.audioFilePath)
                                if (file.exists()) {
                                    file.delete()
                                }
                                db.meetingDao().deleteMeeting(meeting!!)
                                withContext(Dispatchers.Main) {
                                    showDeleteDialog = false
                                    onBack()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF111111)
            )
        }
    }
}

@Composable
fun BrainBulbLoader(text: String = "AI is thinking...") {
    val infiniteTransition = rememberInfiniteTransition()
    val fillRatio by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Unfilled outline
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = Color.DarkGray.copy(alpha = 0.4f)
                )
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.5f).padding(top = 8.dp),
                    tint = Color.DarkGray.copy(alpha = 0.4f)
                )
            }
            
            // Filled portion, clipped from bottom safely
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val fillHeight = size.height * fillRatio
                        val topOffset = size.height - fillHeight
                        clipRect(top = topOffset) {
                            this@drawWithContent.drawContent()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val pulseColor = androidx.compose.ui.graphics.lerp(
                    start = Color(0xFF00C6FF), // Cyan
                    stop = Color(0xFFFF007F),  // Magenta
                    fraction = fillRatio
                )
                
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = pulseColor
                )
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.5f).padding(top = 8.dp),
                    tint = pulseColor
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            color = Color.Cyan.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
