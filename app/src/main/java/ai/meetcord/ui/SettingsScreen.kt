package ai.meetcord.ui

import ai.meetcord.settings.SettingsManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val openAiKey by SettingsManager.openAiKey.collectAsState()
    val anthropicKey by SettingsManager.anthropicKey.collectAsState()
    val geminiKey by SettingsManager.geminiKey.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "AI Providers",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure API keys for Cloud Intelligence.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                ApiKeyInput(
                    label = "OpenAI API Key (ChatGPT)",
                    value = openAiKey,
                    onValueChange = { SettingsManager.setOpenAiKey(it) },
                    placeholder = "sk-..."
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                ApiKeyInput(
                    label = "Anthropic API Key (Claude)",
                    value = anthropicKey,
                    onValueChange = { SettingsManager.setAnthropicKey(it) },
                    placeholder = "sk-ant-..."
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                ApiKeyInput(
                    label = "Google API Key (Gemini)",
                    value = geminiKey,
                    onValueChange = { SettingsManager.setGeminiKey(it) },
                    placeholder = "AIza..."
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                Text(
                    text = "Preferences",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val autoSummary by SettingsManager.autoGenerateSummary.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF111111), shape = RoundedCornerShape(12.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Generate Summary", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Automatically extract speakers and generate AI insights right when a meeting ends.", color = Color.Gray, fontSize = 14.sp)
                    }
                    Switch(
                        checked = autoSummary,
                        onCheckedChange = { SettingsManager.setAutoGenerateSummary(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFFFFFF))
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                val captureMode by SettingsManager.audioCaptureMode.collectAsState()
                var expanded by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF111111), shape = RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Text("Audio Capture Mode", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when (captureMode) {
                            ai.meetcord.settings.AudioCaptureMode.MIC_ONLY -> "Standard microphone. Best for in-person meetings."
                            ai.meetcord.settings.AudioCaptureMode.VOIP_CALLS -> "Records both sides of WhatsApp, Discord, or Zoom calls seamlessly."
                            ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_ONLY -> "Records YouTube/Spotify audio perfectly. Requires screen cast permission."
                            ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_AND_MIC -> "Mixes internal media audio with your microphone. Requires screen cast permission."
                        },
                        color = Color.Gray, fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            value = when (captureMode) {
                                ai.meetcord.settings.AudioCaptureMode.MIC_ONLY -> "External Microphone (Default)"
                                ai.meetcord.settings.AudioCaptureMode.VOIP_CALLS -> "VoIP Calls (Both Sides)"
                                ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_ONLY -> "Internal Media Only"
                                ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_AND_MIC -> "Internal Media + Microphone"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFFFFF),
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF222222),
                                unfocusedContainerColor = Color(0xFF222222)
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color(0xFF222222))
                        ) {
                            DropdownMenuItem(
                                text = { Text("External Microphone (Default)", color = Color.White) },
                                onClick = { 
                                    SettingsManager.setAudioCaptureMode(ai.meetcord.settings.AudioCaptureMode.MIC_ONLY)
                                    expanded = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("VoIP Calls (Both Sides)", color = Color.White) },
                                onClick = { 
                                    SettingsManager.setAudioCaptureMode(ai.meetcord.settings.AudioCaptureMode.VOIP_CALLS)
                                    expanded = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Internal Media Only", color = Color.White) },
                                onClick = { 
                                    SettingsManager.setAudioCaptureMode(ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_ONLY)
                                    expanded = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Internal Media + Microphone", color = Color.White) },
                                onClick = { 
                                    SettingsManager.setAudioCaptureMode(ai.meetcord.settings.AudioCaptureMode.INTERNAL_MEDIA_AND_MIC)
                                    expanded = false 
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyInput(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    var isEditing by remember { mutableStateOf(value.isBlank()) }
    val isValid = value.isNotBlank()

    AnimatedContent(
        targetState = isEditing,
        label = "api_key_anim"
    ) { editing ->
        if (editing) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = label, color = Color(0xFFCCCCCC), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(placeholder, color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (isValid) {
                            IconButton(onClick = { isEditing = false }) {
                                Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFFFFFFFF))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFFFFF),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF111111),
                        unfocusedContainerColor = Color(0xFF111111),
                        cursorColor = Color(0xFFFFFFFF)
                    ),
                    singleLine = true
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF222222), shape = RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFFFFFF), RoundedCornerShape(12.dp))
                    .clickable { isEditing = true }
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label, color = Color(0xFFFFFFFF), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(text = "Verified", color = Color(0xFFFFFFFF), fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color(0xFFFFFFFF))
                }
            }
        }
    }
}
