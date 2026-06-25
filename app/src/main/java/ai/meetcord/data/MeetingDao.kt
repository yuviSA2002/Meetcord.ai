package ai.meetcord.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert
    suspend fun insertMeeting(meeting: MeetingEntity): Long

    @androidx.room.Update
    suspend fun updateMeeting(meeting: MeetingEntity)

    @androidx.room.Delete
    suspend fun deleteMeeting(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings ORDER BY timestampMs DESC")
    fun getAllMeetings(): Flow<List<MeetingEntity>>
    
    @Query("SELECT * FROM meetings WHERE id = :id LIMIT 1")
    suspend fun getMeetingById(id: Int): MeetingEntity?
}
