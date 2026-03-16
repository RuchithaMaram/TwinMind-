package com.twinmind.recorder.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageUtils {

    private const val MIN_FREE_BYTES = 50 * 1024 * 1024L

    fun hasEnoughStorage(context: Context): Boolean {
        val stat = StatFs(getChunksDir(context).absolutePath)
        return stat.availableBytes >= MIN_FREE_BYTES
    }

    fun getChunksDir(context: Context): File {
        val dir = File(context.filesDir, "chunks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMeetingDir(context: Context, meetingId: String): File {
        val dir = File(getChunksDir(context), meetingId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cleanupMeeting(context: Context, meetingId: String) {
        getMeetingDir(context, meetingId).deleteRecursively()
    }
}
