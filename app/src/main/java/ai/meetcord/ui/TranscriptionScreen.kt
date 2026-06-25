@file:OptIn(ExperimentalLayoutApi::class)
package ai.meetcord.ui

import ai.meetcord.asr.WordTimestamp
import ai.meetcord.ui.theme.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TranscriptionScreen(wordsFlow: StateFlow<List<WordTimestamp>>) {
    val words by wordsFlow.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll logic to the bottom when new words appear
    LaunchedEffect(words.size) {
        if (words.isNotEmpty()) {
            listState.animateScrollToItem(words.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000))))
            .padding(16.dp)
    ) {
        if (words.isEmpty()) {
            EmptyStateAnimation()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 60.dp, bottom = 80.dp) // Space for action bar
            ) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        words.forEachIndexed { index, wordStamp ->
                            // Simulate active highlight if it's the last few words
                            val isRecentlySpoken = index >= words.size - 3 
                            
                            val textColor by animateColorAsState(
                                targetValue = if (isRecentlySpoken) Color.White else Color(0xFF666666),
                                animationSpec = tween(durationMillis = 300),
                                label = "colorTransition"
                            )

                            Text(
                                text = wordStamp.text,
                                color = textColor,
                                fontSize = 38.sp, // Large typography typical of Lyrics UI
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live Indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 8.dp)
                .background(Color(0x3310B981), shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing red dot
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red.copy(alpha = alpha)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE TRANSCRIPT",
                    color = Color(0xFFFFFFFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun EmptyStateAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyState")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Outer glow
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0x663B82F6), Color.Transparent)))
            )
            // Inner circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF))))
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Listening to you...",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Speak into the microphone to see magic happen.",
            color = Color(0xFF888888),
            fontSize = 16.sp
        )
    }
}
