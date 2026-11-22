package com.lingyou.voicenotes.domain

/**
 * Post-ASR enrichment.
 * MVP: [com.lingyou.voicenotes.infra.llm.OnDeviceEnricher] (MediaPipe Gemma / heuristic).
 * Later (non-MVP): a cloud implementation may share this interface — do not wire in MVP.
 */
data class Enrichment(
    val title: String,
    val summary: String,
    val bullets: List<String>,
    val tags: List<String>,
    val transcriptClean: String? = null,
    val modelID: String,
) {
    companion object {
        val EMPTY = Enrichment("", "", emptyList(), emptyList(), null, "")
    }
}

interface Enricher {
    val modelID: String
    suspend fun isModelReady(): Boolean
    suspend fun enrich(transcript: String): Enrichment
    /** Unload weights after use (serial pipeline with ASR). */
    suspend fun unload()
}

sealed class EnricherException(message: String) : Exception(message) {
    data object ModelNotReady : EnricherException("整理模型尚未下载或不可用")
    data object EmptyTranscript : EnricherException("没有可整理的转写文本")
    class GenerationFailed(detail: String) : EnricherException("整理失败：$detail")
    data object InvalidJson : EnricherException("模型输出无法解析为结构化结果")
    data object Cancelled : EnricherException("整理已取消")
}
