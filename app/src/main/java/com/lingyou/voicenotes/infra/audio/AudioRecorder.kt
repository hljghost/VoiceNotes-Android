package com.lingyou.voicenotes.infra.audio

import android.media.MediaRecorder
import java.io.File
import java.util.UUID

/**
 * Records AAC/M4A at 16 kHz mono when the device supports it.
 */
class AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    val isRecording: Boolean get() = recorder != null

    @Suppress("DEPRECATION")
    fun start(outputDir: File): File {
        cancel()
        outputDir.mkdirs()
        val file = File(outputDir, "${UUID.randomUUID()}.m4a")
        val mr = MediaRecorder()
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioChannels(1)
        mr.setAudioSamplingRate(16_000)
        mr.setAudioEncodingBitRate(64_000)
        mr.setOutputFile(file.absolutePath)
        mr.prepare()
        mr.start()
        recorder = mr
        outputFile = file
        startedAtMs = System.currentTimeMillis()
        return file
    }

    /** Returns (file, durationMs) or null if nothing usable was recorded. */
    fun stop(): Pair<File, Int>? {
        val file = outputFile ?: return null
        val duration = (System.currentTimeMillis() - startedAtMs).toInt().coerceAtLeast(0)
        return try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: RuntimeException) {
                    // recording too short
                }
                release()
            }
            recorder = null
            outputFile = null
            if (!file.exists() || file.length() < 64L) {
                file.delete()
                null
            } else {
                file to duration
            }
        } catch (_: Exception) {
            cancel()
            null
        }
    }

    fun cancel() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
