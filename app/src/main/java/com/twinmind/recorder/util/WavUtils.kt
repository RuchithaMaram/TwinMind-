package com.twinmind.recorder.util

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object WavUtils {

    fun writeWavFile(pcmData: ByteArray, outputFile: File, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16) {
        FileOutputStream(outputFile).use { out ->
            val dataSize = pcmData.size
            val totalSize = dataSize + 36
            out.write(buildWavHeader(dataSize, totalSize, sampleRate, channels, bitsPerSample))
            out.write(pcmData)
        }
    }

    fun appendWavHeader(file: File, pcmDataSize: Int, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            val totalSize = pcmDataSize + 36
            raf.write(buildWavHeader(pcmDataSize, totalSize, sampleRate, channels, bitsPerSample))
        }
    }

    private fun buildWavHeader(
        dataSize: Int,
        totalSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        return ByteArray(44).apply {
            "RIFF".toByteArray().copyInto(this, 0)
            putInt(this, 4, totalSize)
            "WAVE".toByteArray().copyInto(this, 8)
            "fmt ".toByteArray().copyInto(this, 12)
            putInt(this, 16, 16)
            putShort(this, 20, 1)
            putShort(this, 22, channels.toShort())
            putInt(this, 24, sampleRate)
            putInt(this, 28, byteRate)
            putShort(this, 32, blockAlign.toShort())
            putShort(this, 34, bitsPerSample.toShort())
            "data".toByteArray().copyInto(this, 36)
            putInt(this, 40, dataSize)
        }
    }

    private fun putInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset]     = (value and 0xFF).toByte()
        buffer[offset + 1] = (value shr 8 and 0xFF).toByte()
        buffer[offset + 2] = (value shr 16 and 0xFF).toByte()
        buffer[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun putShort(buffer: ByteArray, offset: Int, value: Short) {
        buffer[offset]     = (value.toInt() and 0xFF).toByte()
        buffer[offset + 1] = (value.toInt() shr 8 and 0xFF).toByte()
    }
}
