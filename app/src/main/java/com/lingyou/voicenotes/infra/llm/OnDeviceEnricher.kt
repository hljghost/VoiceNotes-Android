package com.lingyou.voicenotes.infra.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.lingyou.voicenotes.domain.Enricher
import com.lingyou.voicenotes.domain.EnricherException
import com.lingyou.voicenotes.domain.Enrichment
import com.lingyou.voicenotes.infra.modelstore.GemmaModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device enricher.
 * - No Gemma weights → deterministic heuristic (never hits network).
 * - Weights present → MediaPipe LlmInference; on failure → [EnricherException.GenerationFailed].
 * Cloud LLM intentionally not implemented — keep [Enricher] for a later path.
 */
class OnDeviceEnricher(
    private val appContext: Context,
    private val llmRoot: File,
    override val modelID: String = GemmaModelCatalog.MODEL_ID,
    var allowHeuristicFallback: Boolean = true,
) : Enricher {
    private val mutex = Mutex()
    private var llm: LlmInference? = null

    companion object {
        const val HEURISTIC_SUFFIX = "-heuristic"
        const val FALLBACK_SUFFIX = "-mediapipe-fallback-heuristic"
    }

    override suspend fun isModelReady(): Boolean =
        withContext(Dispatchers.IO) { GemmaModelCatalog.isReady(llmRoot) }

    override suspend fun enrich(transcript: String): Enrichment = mutex.withLock {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) throw EnricherException.EmptyTranscript

        val ready = GemmaModelCatalog.isReady(llmRoot)
        if (!ready) {
            if (allowHeuristicFallback) {
                return heuristic(trimmed, HEURISTIC_SUFFIX)
            }
            throw EnricherException.ModelNotReady
        }

        return try {
            withContext(Dispatchers.Default) {
                val text = generateJson(trimmed)
                parseEnrichment(text, trimmed)
            }
        } catch (e: EnricherException) {
            throw e
        } catch (e: Exception) {
            if (allowHeuristicFallback) {
                // Emulator / missing NPU / MediaPipe init failure → heuristic tagged distinctly
                heuristic(trimmed, FALLBACK_SUFFIX)
            } else {
                throw EnricherException.GenerationFailed(e.message ?: e.toString())
            }
        }
    }

    override suspend fun unload() = mutex.withLock {
        try {
            llm?.close()
        } catch (_: Exception) {
        }
        llm = null
    }

    private fun generateJson(transcript: String): String {
        val engine = loadLlm()
        val prompt = """
            你是中文语音速记整理助手。只根据用户给出的转写原文整理，不要编造原文没有的事实。
            只输出一行 JSON 对象，不要 Markdown、不要代码围栏、不要解释。字段：
            {"title":"不超过20字的标题","summary":"一两句摘要","bullets":["要点1","要点2"],"tags":["标签"]}
            转写原文：
            $transcript
        """.trimIndent()
        return engine.generateResponse(prompt).trim()
    }

    private fun loadLlm(): LlmInference {
        llm?.let { return it }
        val model = GemmaModelCatalog.modelFile(llmRoot)
            ?: throw EnricherException.ModelNotReady
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(model.absolutePath)
            .setMaxTokens(512)
            .build()
        return LlmInference.createFromOptions(appContext, options).also { llm = it }
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
