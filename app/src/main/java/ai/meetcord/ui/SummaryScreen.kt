package ai.meetcord.ui

import ai.meetcord.llm.RollingSummarizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SummaryScreen() {
    val summaryList by RollingSummarizer.summaryFlow.collectAsState()
    val summary = summaryList.joinToString("\n")

    val lines = summary.split("\n").filter { it.isNotBlank() }
    val todos = lines.filter { it.trim().startsWith("-") || it.trim().startsWith("*") || it.contains("TODO", ignoreCase = true) }
    val paragraphs = lines.filterNot { it in todos }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Meeting Summary",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (paragraphs.isEmpty() && todos.isEmpty()) {
                item {
                    Text("No summary generated yet. Keep recording!", color = Color.Gray)
                }
            }
            
            if (paragraphs.isNotEmpty()) {
                item {
                    Text(
                        text = paragraphs.joinToString("\n\n"),
                        color = Color(0xFFFFFFFF),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            
            if (todos.isNotEmpty()) {
                item {
                    Text(
                        text = "Action Items",
                        color = Color(0xFFFFFFFF),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                items(todos.size) { index ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
                    ) {
                        Text(
                            text = todos[index].removePrefix("-").removePrefix("*").trim(),
                            modifier = Modifier.padding(16.dp),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
