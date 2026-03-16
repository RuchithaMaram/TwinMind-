package com.twinmind.recorder.data.repository

import android.util.Log
import com.twinmind.recorder.BuildConfig
import com.twinmind.recorder.data.local.dao.AudioChunkDao
import com.twinmind.recorder.data.local.dao.TranscriptDao
import com.twinmind.recorder.data.local.entity.AudioChunkEntity
import com.twinmind.recorder.data.local.entity.ChunkStatus
import com.twinmind.recorder.data.local.entity.TranscriptEntity
import com.twinmind.recorder.data.remote.GeminiApiService
import com.twinmind.recorder.data.remote.GeminiContent
import com.twinmind.recorder.data.remote.GeminiPart
import com.twinmind.recorder.data.remote.GeminiRequest
import com.twinmind.recorder.data.remote.WhisperApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.source
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TranscriptionRepo"
private const val MAX_RETRY_COUNT = 3

@Singleton
class TranscriptionRepository @Inject constructor(
    private val audioChunkDao: AudioChunkDao,
    private val transcriptDao: TranscriptDao,
    private val whisperApi: WhisperApiService,
    private val geminiApi: GeminiApiService,
) {

    fun observeTranscripts(meetingId: String): Flow<List<TranscriptEntity>> =
        transcriptDao.observeByMeeting(meetingId)

    suspend fun transcribeChunk(chunk: AudioChunkEntity): Result<String> =
        withContext(Dispatchers.IO) {
            audioChunkDao.updateStatus(chunk.id, ChunkStatus.UPLOADING)
            runCatching {
                val text = mockTranscription(chunk)
                transcriptDao.insert(
                    TranscriptEntity(
                        chunkId = chunk.id,
                        meetingId = chunk.meetingId,
                        sequenceNumber = chunk.sequenceNumber,
                        text = text
                    )
                )
                audioChunkDao.updateStatus(chunk.id, ChunkStatus.DONE)
                text
            }.onFailure { e ->
                Log.e(TAG, "Transcription failed for chunk ${chunk.id}", e)
                audioChunkDao.markFailedAndIncrement(chunk.id)
            }
        }

    suspend fun retryAllFailedChunks(meetingId: String) {
        audioChunkDao.resetAllToRetry(meetingId)
        val chunks = audioChunkDao.getByMeeting(meetingId)
        for (chunk in chunks) {
            if (chunk.status != ChunkStatus.DONE) {
                transcribeChunk(chunk)
            }
        }
    }

    suspend fun getFullTranscript(meetingId: String): String {
        return transcriptDao.getByMeeting(meetingId)
            .sortedBy { it.sequenceNumber }
            .joinToString(" ") { it.text }
    }

    private suspend fun transcribeWithWhisper(chunk: AudioChunkEntity): String {
        val file = File(chunk.filePath)
        val requestBody = file.asRequestBody("audio/wav".toMediaType())
        val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
        val model = "whisper-1".toRequestBody("text/plain".toMediaType())
        val format = "json".toRequestBody("text/plain".toMediaType())
        return whisperApi.transcribe(part, model, format).text
    }

    private suspend fun mockTranscription(chunk: AudioChunkEntity): String {
        val samples = listOf(
            "This is a sample transcript for chunk ${chunk.sequenceNumber}.",
            "The meeting discussed quarterly goals and product roadmap.",
            "Action items were assigned to the engineering team.",
            "We reviewed the latest metrics and user feedback.",
            "Next steps include finalizing the API integration and testing.",
        )
        return samples[chunk.sequenceNumber % samples.size]
    }

    suspend fun generateSummary(
        meetingId: String,
        fullTranscript: String,
        onPartialUpdate: suspend (title: String, summary: String, actionItems: List<String>, keyPoints: List<String>) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mockSummaryStream(onPartialUpdate)
        }.onFailure { Log.e(TAG, "Summary generation failed", it) }
    }

    private suspend fun streamGeminiSummary(
        transcript: String,
        onPartialUpdate: suspend (String, String, List<String>, List<String>) -> Unit
    ) {
        val prompt = buildSummaryPrompt(transcript)
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt))))
        )
        val response = geminiApi.streamContent(
            apiKey = BuildConfig.GEMINI_API_KEY,
            alt = "sse",
            request = request
        )
        val body = response.body() ?: throw Exception("Empty response body")

        val accumulatedText = StringBuilder()
        body.byteStream().source().buffer().use { bufferedSource ->
            while (!bufferedSource.exhausted()) {
                val line = bufferedSource.readUtf8Line() ?: continue
                if (line.startsWith("data:")) {
                    val json = line.removePrefix("data:").trim()
                    if (json == "[DONE]") break
                    try {
                        val parsed = com.google.gson.Gson().fromJson(
                            json,
                            com.twinmind.recorder.data.remote.GeminiResponse::class.java
                        )
                        val chunk = parsed.candidates
                            ?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                        accumulatedText.append(chunk)
                        parseSummaryFields(accumulatedText.toString()).let { fields ->
                            onPartialUpdate(fields.first, fields.second, fields.third, fields.fourth)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun parseSummaryFields(raw: String): Quad<String, String, List<String>, List<String>> {
        val title = Regex("(?i)##?\\s*Title[:\\s]+(.*?)(?=##|$)", RegexOption.DOT_MATCHES_ALL)
            .find(raw)?.groupValues?.get(1)?.trim() ?: ""
        val summary = Regex("(?i)##?\\s*Summary[:\\s]+(.*?)(?=##|$)", RegexOption.DOT_MATCHES_ALL)
            .find(raw)?.groupValues?.get(1)?.trim() ?: raw.take(300)
        val actionItems = Regex("(?i)-\\s+(.+?)(?=\\n-|\\n#|$)")
            .findAll(
                Regex("(?i)##?\\s*Action Items[:\\s]+(.*?)(?=##|$)", RegexOption.DOT_MATCHES_ALL)
                    .find(raw)?.groupValues?.get(1) ?: ""
            ).map { it.groupValues[1].trim() }.toList()
        val keyPoints = Regex("(?i)-\\s+(.+?)(?=\\n-|\\n#|$)")
            .findAll(
                Regex("(?i)##?\\s*Key Points[:\\s]+(.*?)(?=##|$)", RegexOption.DOT_MATCHES_ALL)
                    .find(raw)?.groupValues?.get(1) ?: ""
            ).map { it.groupValues[1].trim() }.toList()
        return Quad(title, summary, actionItems, keyPoints)
    }

    private suspend fun mockSummaryStream(
        onPartialUpdate: suspend (String, String, List<String>, List<String>) -> Unit
    ) {
        val title = "Team Sync — Q4 Planning"
        val summaryParts = listOf(
            "The team discussed",
            " quarterly objectives",
            " and aligned on key",
            " priorities for the roadmap.",
            " Engineering committed to",
            " two new feature releases.",
        )
        val actionItems = listOf(
            "Finalize API integration by Friday",
            "Schedule user testing session",
            "Update project documentation",
        )
        val keyPoints = listOf(
            "Q4 goals defined and approved",
            "New hire onboarding starts next week",
            "Performance review cycle begins in November",
        )

        var accSummary = ""
        for (part in summaryParts) {
            delay(80L)
            accSummary += part
            onPartialUpdate(title, accSummary, emptyList(), emptyList())
        }
        delay(80L)
        onPartialUpdate(title, accSummary, actionItems, emptyList())
        delay(80L)
        onPartialUpdate(title, accSummary, actionItems, keyPoints)
    }

    private fun buildSummaryPrompt(transcript: String) = """
        You are a meeting summarizer. Analyze the following transcript and produce structured output.
        
        Transcript:
        $transcript
        
        Respond ONLY in this exact format:
        
        ## Title
        [A concise meeting title]
        
        ## Summary
        [2-4 sentence paragraph summarizing the meeting]
        
        ## Action Items
        - [Action item 1]
        - [Action item 2]
        - [More if applicable]
        
        ## Key Points
        - [Key point 1]
        - [Key point 2]
        - [More if applicable]
    """.trimIndent()
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)