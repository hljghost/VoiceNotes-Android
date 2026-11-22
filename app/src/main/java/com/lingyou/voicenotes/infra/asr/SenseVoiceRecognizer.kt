package com.lingyou.voicenotes.infra.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.lingyou.voicenotes.domain.SpeechRecognizer
import com.lingyou.voicenotes.domain.SpeechRecognizerException
import com.lingyou.voicenotes.infra.audio.AudioSampleLoader
import com.lingyou.voicenotes.infra.modelstore.SenseVoiceModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * sherpa-onnx SenseVoice Small offline ASR.
 * Layout: Models/ASR/sensevoice-small/{model.int8.onnx, tokens.txt}
 */
class SenseVoiceRecognizer(
    private val asrRoot: File,
    override val modelID: String = SenseVoiceModelCatalog.MODEL_ID,
) : SpeechRecognizer {
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    private val bundleDir: File get() = SenseVoiceModelCatalog.bundleDir(asrRoot)
    private val modelFile: File get() = File(bundleDir, SenseVoiceModelCatalog.MODEL_FILE)
    private val tokensFile: File get() = File(bundleDir, SenseVoiceModelCatalog.TOKENS_FILE)

    override suspend fun isModelReady(): Boolean =
        withContext(Dispatchers.IO) { SenseVoiceModelCatalog.isReady(asrRoot) }

    override suspend fun transcribe(audioFile: File): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (!SenseVoiceModelCatalog.isReady(asrRoot)) {
                throw SpeechRecognizerException.ModelNotReady
            }
            val samples = try {
                AudioSampleLoader.loadMonoFloat32(audioFile, 16_000)
            } catch (e: Exception) {
                throw SpeechRecognizerException.UnsupportedAudio
            }
            if (samples.floats.isEmpty()) {
                throw SpeechRecognizerException.UnsupportedAudio
            }
            val engine = loadRecognizer()
            val stream = engine.createStream()
            try {
                stream.acceptWaveform(samples.floats, samples.sampleRate)
                engine.decode(stream)
                val text = engine.getResult(stream).text.trim()
                if (text.isEmpty()) {
                    throw SpeechRecognizerException.EngineFailure("空转写结果（可能太短或无语音）")
                }
                text
            } catch (e: SpeechRecognizerException) {
                throw e
            } catch (e: Exception) {
                throw SpeechRecognizerException.EngineFailure(e.message ?: e.toString())
            } finally {
                stream.release()
            }
        }
    }

    override suspend fun unload() = mutex.withLock {
        recognizer?.release()
        recognizer = null
    }

    private fun loadRecognizer(): OfflineRecognizer {
        recognizer?.let { return it }
        if (!modelFile.exists() || !tokensFile.exists()) {
            throw SpeechRecognizerException.ModelNotReady
        }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelFile.absolutePath,
                    language = "zh",
                    useInverseTextNormalization = true,
                ),
                tokens = tokensFile.absolutePath,
                numThreads = 2,
                provider = "cpu",
                debug = false,
            ),
            decodingMethod = "greedy_search",
        )
        return OfflineRecognizer(config = config).also { recognizer = it }
    }
}
