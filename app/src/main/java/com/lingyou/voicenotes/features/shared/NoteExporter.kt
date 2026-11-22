package com.lingyou.voicenotes.features.shared

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lingyou.voicenotes.infra.persistence.AppPaths
import com.lingyou.voicenotes.infra.persistence.NoteEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NoteExporter {
    fun formatText(note: NoteEntity): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val bullets = note.bullets.joinToString("\n") { "· $it" }
        return buildString {
            appendLine(note.displayTitle)
            appendLine()
            appendLine("时间：${df.format(Date(note.createdAt))}")
            appendLine("状态：${note.status.displayName}")
            if (!note.summary.isNullOrBlank()) {
                appendLine()
                appendLine("摘要")
                appendLine(note.summary)
            }
            if (note.bullets.isNotEmpty()) {
                appendLine()
                appendLine("要点")
                appendLine(bullets)
            }
            if (!note.transcriptRaw.isNullOrBlank()) {
                appendLine()
                appendLine("原文转写")
                appendLine(note.transcriptRaw)
            }
            appendLine()
            appendLine("— VoiceNotes（仅本机）")
        }
    }

    fun share(context: Context, paths: AppPaths, note: NoteEntity) {
        val text = formatText(note)
        val file = File(paths.exportsDir, "voicenote-${note.id.take(8)}.txt")
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.displayTitle)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出笔记"))
    }
}
