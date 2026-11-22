package com.lingyou.voicenotes.infra.persistence

import android.content.Context
import java.io.File

class AppPaths(context: Context) {
    private val root: File = File(context.filesDir, "VoiceNotes").also { it.mkdirs() }

    val audioDir: File = File(root, "Audio").also { it.mkdirs() }
    val modelsDir: File = File(root, "Models").also { it.mkdirs() }
    val asrDir: File = File(modelsDir, "ASR").also { it.mkdirs() }
    val llmDir: File = File(modelsDir, "LLM").also { it.mkdirs() }
    val exportsDir: File = File(context.filesDir, "exports").also { it.mkdirs() }

    fun audioFile(relative: String): File = File(audioDir, relative)
}
