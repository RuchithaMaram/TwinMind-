package com.twinmind.recorder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.twinmind.recorder.data.local.dao.AudioChunkDao
import com.twinmind.recorder.data.local.dao.MeetingDao
import com.twinmind.recorder.data.local.dao.TranscriptDao
import com.twinmind.recorder.data.local.entity.AudioChunkEntity
import com.twinmind.recorder.data.local.entity.ChunkStatus
import com.twinmind.recorder.data.local.entity.MeetingEntity
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.data.local.entity.TranscriptEntity

class Converters {
    @TypeConverter fun fromMeetingStatus(value: MeetingStatus): String = value.name
    @TypeConverter fun toMeetingStatus(value: String): MeetingStatus = MeetingStatus.valueOf(value)
    @TypeConverter fun fromChunkStatus(value: ChunkStatus): String = value.name
    @TypeConverter fun toChunkStatus(value: String): ChunkStatus = ChunkStatus.valueOf(value)
}

@Database(
    entities = [MeetingEntity::class, AudioChunkEntity::class, TranscriptEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun transcriptDao(): TranscriptDao
}
