package com.lingyou.voicenotes.infra.llm

import android.content.Context
import com.lingyou.voicenotes.domain.Enricher
import com.lingyou.voicenotes.domain.EnricherException
import com.lingyou.voicenotes.domain.Enrichment
import com.lingyou.voicenotes.infra.modelstore.ModelPreferences
import com.lingyou.voicenotes.infra.modelstore.QwenModelCatalog
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device enricher — **Qwen3.5-2B Q4_K_M** via llama.cpp.
 *
 * - No GGUF weights → deterministic heuristic (never hits network), `…-heuristic`
 * - Weights present + selected as active → llama.cpp generate JSON; on failure → `…-llamacpp-fallback-heuristic`
 * - Cloud LLM intentionally not implemented — keep [Enricher] for a later path.
 */
class OnDeviceEnricher(
    @Suppress("UNUSED_PARAMETER") private val appContext: Context,
    private val llmRoot: File,
    private val modelPrefs: ModelPreferences? = null,
    override val modelID: String = QwenModelCatalog.MODEL_ID,
    var allowHeuristicFallback: Boolean = true,
) : Enricher {
    private val mutex = Mutex()
    private var model: LlamaModel? = null

    companion object {
        const val HEURISTIC_SUFFIX = "-heuristic"
        const val FALLBACK_SUFFIX = "-llamacpp-fallback-heuristic"
        private const val MAX_TOKENS = 256
    }

    private fun isSelectedActive(): Boolean {
        val active = modelPrefs?.activeLlmModelId
        // No prefs (tests) or empty → treat installed Qwen as active (auto-heal path).
        return active.isNullOrEmpty() || active == QwenModelCatalog.MODEL_ID
    }

    override suspend fun isModelReady(): Boolean =
        withContext(Dispatchers.IO) {
            QwenModelCatalog.isReady(llmRoot) && isSelectedActive()
        }

    override suspend fun enrich(transcript: String): Enrichment = mutex.withLock {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) throw EnricherException.EmptyTranscript

        if (!QwenModelCatalog.isReady(llmRoot) || !isSelectedActive()) {
            if (allowHeuristicFallback) {
                return heuristic(trimmed, HEURISTIC_SUFFIX)
            }
            throw EnricherException.ModelNotReady
        }

        return try {
            val text = generateJson(trimmed)
            parseEnrichment(text, trimmed)
        } catch (e: EnricherException) {
            throw e
        } catch (e: Exception) {
            if (allowHeuristicFallback) {
                // Emulator (no arm64 native) / OOM / arch mismatch → tagged heuristic
                heuristic(trimmed, FALLBACK_SUFFIX)
            } else {
                throw EnricherException.GenerationFailed(e.message ?: e.toString())
            }
        }
    }

    override suspend fun unload() = mutex.withLock {
        val loaded = model ?: return@withLock
        try {
            Llama.releaseModel(loaded)
        } catch (_: Exception) {
        }
        model = null
    }

    private suspend fun generateJson(transcript: String): String {
        val loaded = loadModel()
        val system = """
            你是中文语音速记整理助手。只根据用户给出的转写原文整理，不要编造原文没有的事实。
            不要思考过程，不要 Markdown，不要代码围栏。只输出一行 JSON 对象，字段：
            {"title":"不超过20字的标题","summary":"一两句摘要","bullets":["要点1","要点2"],"tags":["标签"]}
        """.trimIndent()
        val prompt = "转写原文：\n$transcript"
        val result = Llama.complete(
            loaded,
            prompt = prompt,
            systemPrompt = system,
            maxTokens = MAX_TOKENS,
        )
        return result.text.trim()
    }

    private suspend fun loadModel(): LlamaModel {
        model?.let { if (it.isLoaded) return it }
        val file = QwenModelCatalog.modelFile(llmRoot)
            ?: throw EnricherException.ModelNotReady
        val loaded = Llama.loadModel(
            modelPath = file.absolutePath,
            config = LlamaConfig(
                contextSize = 2048,
                threads = 4,
                gpuLayers = 0,
                temperature = 0.2f,
                topP = 0.9f,
                topK = 40,
                seed = 42,
            ),
        )
        model = loaded
        return loaded
    }

    private fun parseEnrichment(raw: String, fallback: String): Enrichment {
        val jsonText = extractJsonObject(raw) ?: raw
        val obj = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            throw EnricherException.InvalidJson
        }
        val title = obj.optString("title").trim()
        val summary = obj.optString("summary").trim()
        val bullets = obj.optJSONArray("bullets").toStringList()
        val tags = obj.optJSONArray("tags").toStringList()
        if (title.isEmpty() && summary.isEmpty() && bullets.isEmpty()) {
            throw EnricherException.InvalidJson
        }
        return Enrichment(
            title = title.ifEmpty { fallback.take(24) },
            summary = summary.ifEmpty { fallback.take(120) },
            bullets = bullets.ifEmpty { listOf(fallback.take(40)) },
            tags = tags.ifEmpty { listOf("速记") },
            transcriptClean = fallback,
            modelID = modelID,
        )
    }

    private fun heuristic(transcript: String, suffix: String): Enrichment {
        val title = transcript.substringBefore("。").take(24).ifEmpty { "速记" }
        val summary = transcript.take(120)
        val bullets = transcript
            .split('。', '！', '？', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(5)
        return Enrichment(
            title = title,
            summary = summary,
            bullets = bullets,
            tags = listOf("速记", "启发式"),
            transcriptClean = transcript,
            modelID = "$modelID$suffix",
        )
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end >= start) return text.substring(start, end + 1)
        return null
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val s = optString(i).trim()
                if (s.isNotEmpty()) add(s)
            }
        }
    }
}
