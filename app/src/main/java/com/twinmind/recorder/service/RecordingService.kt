package com.twinmind.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.twinmind.recorder.MainActivity
import com.twinmind.recorder.data.local.dao.AudioChunkDao
import com.twinmind.recorder.data.local.dao.MeetingDao
import com.twinmind.recorder.data.local.entity.AudioChunkEntity
import com.twinmind.recorder.data.local.entity.ChunkStatus
import com.twinmind.recorder.data.local.entity.MeetingStatus
import com.twinmind.recorder.util.StorageUtils
import com.twinmind.recorder.util.WavUtils
import com.twinmind.recorder.worker.TranscriptionWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private const val TAG = "RecordingService"
const val CHANNEL_ID        = "recording_channel"
const val NOTIFICATION_ID   = 1001

private const val SAMPLE_RATE    = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT
private const val BYTES_PER_SAMPLE = 2

private val CHUNK_BYTES   = SAMPLE_RATE * BYTES_PER_SAMPLE * 30
private val OVERLAP_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * 2
private const val MIN_CHUNK_BYTES = 4096

private const val SILENCE_THRESHOLD_RMS = 150.0
private const val SILENCE_WARNING_MS    = 10_000L

const val ACTION_STOP   = "com.twinmind.recorder.ACTION_STOP"
const val ACTION_RESUME = "com.twinmind.recorder.ACTION_RESUME"
const val EXTRA_MEETING_ID = "meeting_id"

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var meetingDao: MeetingDao
    @Inject lateinit var audioChunkDao: AudioChunkDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private val isRecording  = AtomicBoolean(false)
    private val isPaused     = AtomicBoolean(false)
    private val isStopping   = AtomicBoolean(false)

    private var meetingId      = ""
    private var chunkSequence  = 0
    private var sessionStartMs = 0L
    private var elapsedMs      = 0L
    private var status         = RecordingStatus.IDLE

    private lateinit var audioManager: AudioManager
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var phoneStateCallback: Any? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseRecording(RecordingStatus.PAUSED_FOCUS)
            AudioManager.AUDIOFOCUS_GAIN           -> resumeRecording()
        }
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    val msg = if (state == 1) "Wired headset connected"
                    else "Wired headset disconnected"
                    showNotification(status, msg)
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val s = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                    val msg = if (s == BluetoothHeadset.STATE_CONNECTED)
                        "Bluetooth headset connected" else "Bluetooth headset disconnected"
                    showNotification(status, msg)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager       = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        telephonyManager   = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        acquireWakeLock()
        registerReceivers()
        registerPhoneListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP   -> stopRecordingAndFinalize()
            ACTION_RESUME -> resumeRecording()
            else -> {
                meetingId = intent?.getStringExtra(EXTRA_MEETING_ID)
                    ?: UUID.randomUUID().toString()
                if (!isRecording.get()) {
                    startForeground(NOTIFICATION_ID, buildNotification(RecordingStatus.RECORDING))
                    startRecording()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording.get()) stopRecordingAndFinalize()
        serviceScope.cancel()
        releaseWakeLock()
        unregisterReceiversSafe()
        unregisterPhoneListener()
    }

    private fun startRecording() {
        if (!StorageUtils.hasEnoughStorage(this)) {
            showErrorNotification("Recording stopped - Low storage")
            serviceScope.launch { meetingDao.setError(meetingId, "Low storage", MeetingStatus.ERROR) }
            stopSelf()
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
            maxOf(minBuf * 4, 65536)
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            stopSelf()
            return
        }

        isRecording.set(true)
        isPaused.set(false)
        sessionStartMs = System.currentTimeMillis()
        status = RecordingStatus.RECORDING

        audioRecord?.startRecording()
        showNotification(status)
        startTimer()

        recordingJob = serviceScope.launch { recordLoop() }
    }

    private suspend fun recordLoop() {
        val readBuffer  = ByteArray(4096)
        val chunkBuffer = java.io.ByteArrayOutputStream(CHUNK_BYTES + OVERLAP_BYTES)
        var silenceStartMs    = 0L
        var silenceWarningShown = false
        var totalBytesWritten = 0L
        val storageCheckEvery = (SAMPLE_RATE * BYTES_PER_SAMPLE * 5).toLong()

        Log.d(TAG, "recordLoop started")

        try {
            while (isRecording.get()) {
                if (isPaused.get()) { delay(100); continue }

                val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                if (bytesRead <= 0) continue

                val pcmSlice = readBuffer.copyOf(bytesRead)
                chunkBuffer.write(pcmSlice)
                totalBytesWritten += bytesRead

                val rms = calculateRms(pcmSlice)
                if (rms > SILENCE_THRESHOLD_RMS) {
                    silenceStartMs      = 0L
                    silenceWarningShown = false
                } else {
                    if (silenceStartMs == 0L) silenceStartMs = System.currentTimeMillis()
                    if (System.currentTimeMillis() - silenceStartMs >= SILENCE_WARNING_MS
                        && !silenceWarningShown) {
                        silenceWarningShown = true
                        showNotification(status, "No audio detected - Check microphone")
                    }
                }

                if (totalBytesWritten % storageCheckEvery < bytesRead) {
                    if (!StorageUtils.hasEnoughStorage(this@RecordingService)) {
                        showErrorNotification("Recording stopped - Low storage")
                        meetingDao.setError(meetingId, "Low storage", MeetingStatus.ERROR)
                        isRecording.set(false)
                        break
                    }
                }

                if (chunkBuffer.size() >= CHUNK_BYTES) {
                    val pcm     = chunkBuffer.toByteArray()
                    val overlap = pcm.takeLast(OVERLAP_BYTES).toByteArray()
                    saveChunkAndEnqueue(pcm)
                    chunkBuffer.reset()
                    chunkBuffer.write(overlap)
                }
            }
        } finally {
            val remaining = chunkBuffer.toByteArray()
            if (remaining.size >= MIN_CHUNK_BYTES) {
                Log.d(TAG, "Saving final chunk: ${remaining.size} bytes")
                saveChunkAndEnqueue(remaining)
            } else {
                Log.w(TAG, "Final chunk too small (${remaining.size} bytes), skipping")
            }
            Log.d(TAG, "recordLoop finished, total chunks=$chunkSequence")
        }
    }

    private suspend fun saveChunkAndEnqueue(pcm: ByteArray) {
        val chunkId   = UUID.randomUUID().toString()
        val chunkFile = File(
            StorageUtils.getMeetingDir(this, meetingId),
            "chunk_${chunkSequence}_$chunkId.wav"
        )
        WavUtils.writeWavFile(pcm, chunkFile, SAMPLE_RATE)

        val entity = AudioChunkEntity(
            id             = chunkId,
            meetingId      = meetingId,
            sequenceNumber = chunkSequence++,
            filePath       = chunkFile.absolutePath,
            durationMs     = pcm.size.toLong() * 1000L / (SAMPLE_RATE * BYTES_PER_SAMPLE),
            status         = ChunkStatus.PENDING
        )
        audioChunkDao.insert(entity)
        Log.d(TAG, "Chunk saved: seq=${entity.sequenceNumber} size=${pcm.size}")
    }

    fun stopRecordingAndFinalize() {
        if (!isStopping.compareAndSet(false, true)) return
        Log.d(TAG, "stopRecordingAndFinalize called")

        isRecording.set(false)

        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null

        timerJob?.cancel()
        status = RecordingStatus.STOPPED

        serviceScope.launch {
            recordingJob?.join()
            Log.d(TAG, "recordLoop joined, chunkSequence=$chunkSequence")

            val endMs = System.currentTimeMillis()
            meetingDao.finalizeRecording(meetingId, endMs, elapsedMs, MeetingStatus.TRANSCRIBING)

            TranscriptionWorker.enqueueMeetingAll(applicationContext, meetingId)
            Log.d(TAG, "TranscriptionWorker enqueued for $meetingId")

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRecording(reason: RecordingStatus) {
        if (!isRecording.get() || isPaused.get()) return
        isPaused.set(true)
        status = reason
        serviceScope.launch { meetingDao.updateStatus(meetingId, reason.toMeetingStatus()) }
        showNotification(reason, reason.label, showResume = true)
    }

    private fun resumeRecording() {
        if (!isRecording.get() || !isPaused.get()) return
        isPaused.set(false)
        status = RecordingStatus.RECORDING
        serviceScope.launch { meetingDao.updateStatus(meetingId, MeetingStatus.RECORDING) }
        showNotification(status)
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (isRecording.get()) {
                delay(1000)
                if (!isPaused.get()) elapsedMs += 1000
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            telephonyManager.registerTelephonyCallback(mainExecutor, cb)
            phoneStateCallback = cb
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                    handleCallState(state)
            }
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            phoneStateCallback = listener
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (phoneStateCallback as? TelephonyCallback)?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
        } else {
            (phoneStateCallback as? PhoneStateListener)?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> pauseRecording(RecordingStatus.PAUSED_CALL)
            TelephonyManager.CALL_STATE_IDLE ->
                if (status == RecordingStatus.PAUSED_CALL) resumeRecording()
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(headsetReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(headsetReceiver, filter)
        }
    }

    private fun unregisterReceiversSafe() {
        runCatching { unregisterReceiver(headsetReceiver) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = "TwinMind recording service"
                }
            )
        }
    }

    private fun buildNotification(
        recordingStatus: RecordingStatus,
        extraText: String? = null,
        showResume: Boolean = false
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val resumeIntent = PendingIntent.getService(
            this, 2,
            Intent(this, RecordingService::class.java).apply { action = ACTION_RESUME },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(extraText ?: recordingStatus.label)
            .setContentText("TwinMind • Recording in progress")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (showResume) addAction(0, "Resume", resumeIntent)
                addAction(0, "Stop", stopIntent)
            }
            .build()
    }

    private fun showNotification(
        recordingStatus: RecordingStatus,
        extraText: String? = null,
        showResume: Boolean = false
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(recordingStatus, extraText, showResume)
        )
    }

    private fun showErrorNotification(message: String) {
        notificationManager.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Recording stopped")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TwinMind:RecordingWakeLock"
        ).also { it.acquire(4 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun calculateRms(pcm: ByteArray): Double {
        var sum = 0.0
        var i   = 0
        while (i < pcm.size - 1) {
            val sample = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
            i   += 2
        }
        return if (pcm.size > 1) Math.sqrt(sum / (pcm.size / 2)) else 0.0
    }

    companion object {
        fun startIntent(context: Context, meetingId: String) =
            Intent(context, RecordingService::class.java).apply {
                putExtra(EXTRA_MEETING_ID, meetingId)
            }
    }
}

private class ByteArrayOutputStream(initialCapacity: Int = 32) :
    java.io.ByteArrayOutputStream(initialCapacity)

enum class RecordingStatus(val label: String) {
    IDLE("Idle"),
    RECORDING("Recording..."),
    PAUSED_CALL("Paused - Phone call"),
    PAUSED_FOCUS("Paused - Audio focus lost"),
    STOPPED("Stopped");

    fun toMeetingStatus(): MeetingStatus = when (this) {
        RECORDING            -> MeetingStatus.RECORDING
        PAUSED_CALL,
        PAUSED_FOCUS         -> MeetingStatus.PAUSED
        STOPPED              -> MeetingStatus.STOPPED
        IDLE                 -> MeetingStatus.RECORDING
    }
}