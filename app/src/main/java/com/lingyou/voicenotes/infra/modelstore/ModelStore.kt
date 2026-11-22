package com.lingyou.voicenotes.infra.modelstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class ModelKind { ASR, LLM }

data class DownloadProgress(
    val kind: ModelKind,
    val fileName: String,
    val bytesReceived: Long,
    val bytesTotal: Long?,
    val message: String,
)

/**
 * ModelScope-only downloader (modelscope.cn). No HuggingFace / GitHub / hf-mirror.
 */
class ModelStore(
    private val asrRoot: File,
    private val llmRoot: File,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun asrReady(): Boolean = SenseVoiceModelCatalog.isReady(asrRoot)
    fun llmReady(): Boolean = GemmaModelCatalog.isReady(llmRoot)
    fun asrBytes(): Long = SenseVoiceModelCatalog.bytesOnDisk(asrRoot)
    fun llmBytes(): Long = GemmaModelCatalog.bytesOnDisk(llmRoot)

    suspend fun downloadSenseVoice(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (_busy.value) return@withContext
        _busy.value = true
        _lastError.value = null
        try {
            val dir = SenseVoiceModelCatalog.bundleDir(asrRoot).also { it.mkdirs() }
            for (file in SenseVoiceModelCatalog.files) {
                val dest = File(dir, file.fileName)
                if (!force && FileChecksum.matches(dest, file.sha256, file.expectedBytes)) continue
                downloadFirstSuccess(
                    urls = SenseVoiceModelCatalog.remoteUrls(file.fileName),
                    dest = dest,
                    kind = ModelKind.ASR,
                    expectedSha = file.sha256,
                    expectedBytes = file.expectedBytes,
                )
            }
        } catch (e: Exception) {
            _lastError.value = e.message ?: "下载失败，请检查网络后重试（仅 ModelScope）"
            throw e
        } finally {
            _busy.value = false
            _progress.value = null
        }
    }

    suspend fun downloadGemma(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (_busy.value) return@withContext
        _busy.value = true
        _lastError.value = null
        try {
            val dir = GemmaModelCatalog.bundleDir(llmRoot).also { it.mkdirs() }
            for (file in GemmaModelCatalog.files) {
                val dest = File(dir, file.fileName)
                if (!force && GemmaModelCatalog.isReady(llmRoot)) continue
                downloadFirstSuccess(
                    urls = GemmaModelCatalog.remoteUrls(file.fileName),
                    dest = dest,
                    kind = ModelKind.LLM,
                    expectedSha = file.sha256,
                    expectedBytes = null,
                    minBytes = file.expectedBytesMin,
                )
            }
        } catch (e: Exception) {
            _lastError.value = e.message
                ?: "Gemma 下载失败。可稍后重试，或侧载 .task 到 Models/LLM/${GemmaModelCatalog.MODEL_ID}/。未下载时使用启发式整理。"
            throw e
        } finally {
            _busy.value = false
            _progress.value = null
        }
    }

    suspend fun deleteSenseVoice() = withContext(Dispatchers.IO) {
        SenseVoiceModelCatalog.bundleDir(asrRoot).deleteRecursively()
    }

    suspend fun deleteGemma() = withContext(Dispatchers.IO) {
        GemmaModelCatalog.bundleDir(llmRoot).deleteRecursively()
    }

    private fun downloadFirstSuccess(
        urls: List<String>,
        dest: File,
        kind: ModelKind,
        expectedSha: String?,
        expectedBytes: Long?,
        minBytes: Long? = null,
    ) {
        require(urls.all { it.contains("modelscope.cn") }) {
            "禁止非 ModelScope 源"
        }
        val tmp = File(dest.parentFile, dest.name + ".part")
        var lastError: Exception? = null
        for (url in urls) {
            try {
                downloadOne(url, tmp, dest.name, kind)
                if (expectedSha != null && expectedBytes != null) {
                    if (!FileChecksum.matches(tmp, expectedSha, expectedBytes)) {
                        tmp.delete()
                        throw IllegalStateException("校验失败：${dest.name} SHA/大小不匹配，尝试下一镜像…")
                    }
                } else if (minBytes != null && tmp.length() < minBytes) {
                    tmp.delete()
                    throw IllegalStateException("文件过小：${dest.name}")
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                return
            } catch (e: Exception) {
                lastError = e
                tmp.delete()
            }
        }
        throw lastError ?: IllegalStateException("所有 ModelScope 镜像均失败：${dest.name}")
    }

    private fun downloadOne(url: String, dest: File, displayName: String, kind: ModelKind) {
        dest.parentFile?.mkdirs()
        val req = Request.Builder().url(url).header("User-Agent", "VoiceNotes-Android/0.1").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} · $displayName")
            val body = resp.body ?: error("空响应 · $displayName")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(1024 * 256)
                    var received = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        received += n
                        _progress.value = DownloadProgress(
                            kind = kind,
                            fileName = displayName,
                            bytesReceived = received,
                            bytesTotal = total,
                            message = "正在从 ModelScope 下载 $displayName…",
                        )
                    }
                }
            }
        }
    }
}

fun Long.formatBytes(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
