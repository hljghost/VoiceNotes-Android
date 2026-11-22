package com.lingyou.voicenotes.features.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingyou.voicenotes.app.AppDependencies
import com.lingyou.voicenotes.domain.NoteStatus
import com.lingyou.voicenotes.features.shared.NoteExporter
import com.lingyou.voicenotes.features.shared.VNColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    deps: AppDependencies,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val note by deps.noteDao.observeById(noteId).collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var editingTitle by remember { mutableStateOf<String?>(null) }
    var editingSummary by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("详情") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        note?.let { NoteExporter.share(context, deps.paths, it) }
                    },
                ) {
                    Icon(Icons.Default.Share, contentDescription = "导出")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = VNColors.danger)
                }
            },
        )

        val n = note
        if (n == null) {
            Text("笔记不存在", modifier = Modifier.padding(16.dp))
            return
        }

        val title = editingTitle ?: (n.title ?: n.displayTitle)
        val summary = editingSummary ?: (n.summary ?: "")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                n.status.displayName,
                color = VNColors.statusColor(n.status),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { editingTitle = it },
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = summary,
                onValueChange = { editingSummary = it },
                label = { Text("摘要") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            if (n.bullets.isNotEmpty()) {
                Text("要点", fontWeight = FontWeight.SemiBold, color = VNColors.hush)
                n.bullets.forEach { Text("· $it") }
            }
            if (!n.transcriptRaw.isNullOrBlank()) {
                Text("原文转写", fontWeight = FontWeight.SemiBold, color = VNColors.hush)
                Text(n.transcriptRaw!!)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val rel = n.audioRelativePath ?: return@OutlinedButton
                        val file = deps.paths.audioFile(rel)
                        if (playing) {
                            deps.audioPlayer.stop()
                            playing = false
                        } else {
                            deps.audioPlayer.play(file) { playing = false }
                            playing = true
                        }
                    },
                ) {
                    Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Text(if (playing) " 停止" else " 回放")
                }
                if (editingTitle != null || editingSummary != null) {
                    Button(
                        onClick = {
                            scope.launch {
                                deps.pipeline.updateEditable(
                                    noteId = n.id,
                                    title = editingTitle ?: n.title,
                                    summary = editingSummary ?: n.summary,
                                    transcriptRaw = n.transcriptRaw,
                                    bullets = null,
                                )
                                editingTitle = null
                                editingSummary = null
                            }
                        },
                    ) { Text("保存") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (n.status.canRetryAsr) {
                    Button(onClick = { scope.launch { deps.pipeline.retryAsr(n.id) } }) {
                        Text("重试转写")
                    }
                }
                if (n.status.canRetryEnrich || n.status == NoteStatus.READY) {
                    OutlinedButton(onClick = { scope.launch { deps.pipeline.retryEnrich(n.id) } }) {
                        Text("重试整理")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "ASR: ${n.asrModelID ?: "—"}  ·  LLM: ${n.llmModelID ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = VNColors.hush,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除笔记？") },
            text = { Text("将删除本条笔记与本地音频。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            deps.pipeline.deleteNote(noteId)
                            confirmDelete = false
                            onBack()
                        }
                    },
                ) { Text("删除", color = VNColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
