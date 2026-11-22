package com.lingyou.voicenotes.infra.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class AudioSamples(
    val floats: FloatArray,
    val sampleRate: Int,
)

/** Decode local audio to mono float32 at [targetSampleRate] (default 16 kHz). */
object AudioSampleLoader {
    fun loadMonoFloat32(file: File, targetSampleRate: Int = 16_000): AudioSamples {
        if (!file.exists()) error("audio missing: ${file.path}")
        // Fast path: raw 16-bit PCM WAV
        if (file.name.endsWith(".wav", ignoreCase = true)) {
            readWavPcm16(file, targetSampleRate)?.let { return it }
        }
        return decodeWithMediaCodec(file, targetSampleRate)
    }

    private fun readWavPcm16(file: File, targetSampleRate: Int): AudioSamples? {
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var offset = 12
        var sampleRate = 16_000
        var channels = 1
        var bits = 16
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val next = offset + 8 + size
            when (id) {
                "fmt " -> {
                    channels = ByteBuffer.wrap(bytes, offset + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    sampleRate = ByteBuffer.wrap(bytes, offset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bits = ByteBuffer.wrap(bytes, offset + 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                }
                "data" -> {
                    dataOffset = offset + 8
                    dataSize = size
                    break
                }
            }
            offset = next
        }
        if (dataOffset < 0 || bits != 16) return null
        val sampleCount = dataSize / 2
        val shorts = ShortArray(sampleCount)
        ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val mono = if (channels == 1) {
            FloatArray(sampleCount) { i -> shorts[i] / 32768f }
        } else {
            val frames = sampleCount / channels
            FloatArray(frames) { i ->
                var sum = 0f
                for (c in 0 until channels) {
                    sum += shorts[i * channels + c] / 32768f
                }
                sum / channels
            }
        }
        val resampled = if (sampleRate == targetSampleRate) mono else resample(mono, sampleRate, targetSampleRate)
        return AudioSamples(resampled, targetSampleRate)
    }

    private fun decodeWithMediaCodec(file: File, targetSampleRate: Int): AudioSamples {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                track = i
                format = f
                break
            }
        }
        require(track >= 0 && format != null) { "no audio track" }
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val pcm = ArrayList<Float>(64_000)
        var inputDone = false
        var outputDone = false
        val info = MediaCodec.BufferInfo()
        val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex >= 0 -> {
                    val out = codec.getOutputBuffer(outIndex)!!
                    if (info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        out.get(bytes)
                        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val shorts = ShortArray(bb.remaining())
                        bb.get(shorts)
                        var i = 0
                        while (i < shorts.size) {
                            var sum = 0f
                            for (c in 0 until channels) {
                                if (i + c < shorts.size) sum += shorts[i + c] / 32768f
                            }
                            pcm.add(sum / channels)
                            i += channels
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        val mono = pcm.toFloatArray()
        val resampled = if (srcRate == targetSampleRate) mono else resample(mono, srcRate, targetSampleRate)
        return AudioSamples(resampled, targetSampleRate)
    }

    private fun resample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (input.isEmpty() || srcRate <= 0) return input
        val outLen = (input.size.toLong() * dstRate / srcRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        val ratio = srcRate.toDouble() / dstRate
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt()
            val i1 = min(i0 + 1, input.lastIndex)
            val frac = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1 - frac) + input[i1] * frac
        }
        return out
    }
}
