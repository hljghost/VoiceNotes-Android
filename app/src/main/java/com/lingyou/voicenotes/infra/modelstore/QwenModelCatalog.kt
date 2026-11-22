package com.lingyou.voicenotes.infra.modelstore

import java.io.File

/**
 * On-device LLM: **Qwen3.5-2B** Q4_K_M GGUF for llama.cpp.
 *
 * - Model ID (app): `qwen3.5-2b-q4-k-m`
 * - Upstream: `Qwen/Qwen3.5-2B` → GGUF Q4_K_M (~1.28 GB)
 * - Mainland download: ModelScope only
 * - Runtime: llama.cpp via `dev.ffmpegkit-maintained:llama-android` (CPU/NEON)
 *
 * Heuristic enricher works without weights.
 */
object QwenModelCatalog {
    const val MODEL_ID = "qwen3.5-2b-q4-k-m"
    /** Canonical on-disk name after download / side-load. */
    const val MODEL_FILE = "Qwen3.5-2B-Q4_K_M.gguf"
    const val MIRROR_LABEL = "ModelScope"
    const val RUNTIME_LABEL = "llama.cpp (CPU/NEON)"
    /** Soft lower bound (~1.1 GB) so side-loaded Q3/Q4 variants still count as present. */
    const val EXPECTED_BYTES_MIN = 1_100_000_000L
    const val EXPECTED_BYTES = 1_280_835_840L
    const val EXPECTED_SHA256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"

    data class Mirror(
        val repo: String,
        /** Remote file name under that repo (may differ; saved as [MODEL_FILE]). */
        val remoteFileName: String,
        val expectedBytes: Long?,
        val sha256: String?,
    )

    /** Prefer unsloth (canonical filename + SHA); diodel as alternate packaging. */
    val mirrors = listOf(
        Mirror(
            repo = "unsloth/Qwen3.5-2B-GGUF",
            remoteFileName = "Qwen3.5-2B-Q4_K_M.gguf",
            expectedBytes = EXPECTED_BYTES,
            sha256 = EXPECTED_SHA256,
        ),
        Mirror(
            repo = "diodel/Qwen3.5-2B-Q4_K_M-GGUF",
            remoteFileName = "qwen3-5-2B-Q4_K_M.gguf",
            expectedBytes = 1_270_808_448L,
            sha256 = "3973c144eb7a185f7d54551a28622fcd0cf293fcbad078817e38bd979a4d69f2",
        ),
    )

    fun remoteUrls(mirror: Mirror): List<String> = listOf(
        "https://www.modelscope.cn/models/${mirror.repo}/resolve/master/${mirror.remoteFileName}",
        "https://www.modelscope.cn/models/${mirror.repo}/resolve/main/${mirror.remoteFileName}",
        "https://www.modelscope.cn/api/v1/models/${mirror.repo}/repo?Revision=master&FilePath=${mirror.remoteFileName}",
    )

    fun bundleDir(llmRoot: File): File = File(llmRoot, MODEL_ID)

    fun modelFile(llmRoot: File): File? {
        val dir = bundleDir(llmRoot)
        val primary = File(dir, MODEL_FILE)
        if (primary.exists() && primary.length() >= EXPECTED_BYTES_MIN) return primary
        return dir.listFiles()
            ?.firstOrNull { it.isFile && it.extension.equals("gguf", ignoreCase = true) && it.length() >= EXPECTED_BYTES_MIN }
    }

    fun isReady(llmRoot: File): Boolean = modelFile(llmRoot) != null

    fun bytesOnDisk(llmRoot: File): Long {
        val dir = bundleDir(llmRoot)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
