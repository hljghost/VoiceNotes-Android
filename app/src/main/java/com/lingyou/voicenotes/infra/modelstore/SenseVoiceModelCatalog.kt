package com.lingyou.voicenotes.infra.modelstore

import java.io.File

/**
 * SenseVoice Small int8 for sherpa-onnx.
 * Mainland downloads: ModelScope only (no HF / GitHub).
 */
object SenseVoiceModelCatalog {
    const val MODEL_ID = "sensevoice-small"
    const val MODEL_FILE = "model.int8.onnx"
    const val TOKENS_FILE = "tokens.txt"
    const val MIRROR_LABEL = "ModelScope"

    val modelScopeMirrorRepos = listOf(
        "pengzhendong/sherpa-onnx-sense-voice-zh-en-ja-ko-yue",
        "chriscrs/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17",
        "vqhvqh/SenseVoice_Small_int8_ONNX",
        "burgess123/sherpa-onnx-SenseVoice-Small-onnx",
        "vcbvcx/SenseVoice_Small_Int8",
        "fengge2024/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
    )

    data class RemoteFile(
        val fileName: String,
        val expectedBytes: Long,
        val sha256: String,
    )

    val files = listOf(
        RemoteFile(
            fileName = MODEL_FILE,
            expectedBytes = 239_233_841L,
            sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
        ),
        RemoteFile(
            fileName = TOKENS_FILE,
            expectedBytes = 315_894L,
            sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
        ),
    )

    fun remoteUrls(fileName: String): List<String> =
        modelScopeMirrorRepos.map { repo ->
            "https://www.modelscope.cn/models/$repo/resolve/master/$fileName"
        }

    fun bundleDir(asrRoot: File): File = File(asrRoot, MODEL_ID)

    fun isReady(asrRoot: File): Boolean {
        val dir = bundleDir(asrRoot)
        return files.all { f ->
            FileChecksum.matches(File(dir, f.fileName), f.sha256, f.expectedBytes)
        }
    }

    fun bytesOnDisk(asrRoot: File): Long {
        val dir = bundleDir(asrRoot)
        return files.sumOf { File(dir, it.fileName).takeIf { f -> f.exists() }?.length() ?: 0L }
    }
}
