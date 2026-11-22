package com.lingyou.voicenotes.infra.modelstore

import java.io.File

/**
 * On-device LLM for Android: MediaPipe / AI Edge Gemma 3 1B INT4 (.task).
 * Mainland: ModelScope mirrors only. Heuristic enricher works without this file.
 *
 * Note: MediaPipe task bundles differ from MLX Qwen weights on iOS.
 * If ModelScope mirror is unavailable, user can side-load the .task into the LLM folder.
 */
object GemmaModelCatalog {
    const val MODEL_ID = "gemma-3-1b-it-int4"
    const val MODEL_FILE = "gemma3-1b-it-int4.task"
    const val MIRROR_LABEL = "ModelScope"

    /** Prefer known ModelScope / community mirrors; empty expected SHA means size-only soft check. */
    val modelScopeMirrorRepos = listOf(
        "AI-ModelScope/gemma-3-1b-it-int4-web",
        "LLM-Research/gemma-3-1b-it",
        "google/gemma-3-1b-it",
    )

    data class RemoteFile(
        val fileName: String,
        val expectedBytesMin: Long,
        val sha256: String?,
    )

    val files = listOf(
        RemoteFile(
            fileName = MODEL_FILE,
            // MediaPipe .task ~500MB–1.1GB depending on packaging; soft lower bound
            expectedBytesMin = 50_000_000L,
            sha256 = null,
        ),
    )

    fun remoteUrls(fileName: String): List<String> =
        modelScopeMirrorRepos.map { repo ->
            "https://www.modelscope.cn/models/$repo/resolve/master/$fileName"
        } + modelScopeMirrorRepos.map { repo ->
            "https://www.modelscope.cn/models/$repo/resolve/main/$fileName"
        }

    fun bundleDir(llmRoot: File): File = File(llmRoot, MODEL_ID)

    fun isReady(llmRoot: File): Boolean {
        val f = File(bundleDir(llmRoot), MODEL_FILE)
        if (!f.exists()) {
            // Also accept any single *.task under the bundle dir (side-load)
            val alt = bundleDir(llmRoot).listFiles()?.firstOrNull { it.extension == "task" }
            return alt != null && alt.length() >= files.first().expectedBytesMin
        }
        return f.length() >= files.first().expectedBytesMin
    }

    fun modelFile(llmRoot: File): File? {
        val primary = File(bundleDir(llmRoot), MODEL_FILE)
        if (primary.exists()) return primary
        return bundleDir(llmRoot).listFiles()?.firstOrNull { it.extension == "task" }
    }

    fun bytesOnDisk(llmRoot: File): Long {
        val dir = bundleDir(llmRoot)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
