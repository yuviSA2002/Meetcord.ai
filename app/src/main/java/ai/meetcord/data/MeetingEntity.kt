package ai.meetcord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timestampMs: Long,
    val audioFilePath: String,
    val transcriptJson: String,
    val summary: String,
    val actionItems: String,
    val aiModelUsed: String
)
