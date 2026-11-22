package com.lingyou.voicenotes.features.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingyou.voicenotes.app.AppDependencies
import com.lingyou.voicenotes.features.shared.NoteExporter
import com.lingyou.voicenotes.features.shared.VNColors
import com.lingyou.voicenotes.infra.persistence.NoteEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    deps: AppDependencies,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }

    val allNotes by deps.noteDao.observeAll().collectAsState(initial = emptyList())
    val notes = remember(allNotes, query) {
        val q = query.trim()
        if (q.isEmpty()) allNotes
        else allNotes.filter {
            (it.title?.contains(q, true) == true) ||
                (it.summary?.contains(q, true) == true) ||
                (it.transcriptRaw?.contains(q, true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Text(
            "笔记",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索标题 / 摘要 / 原文") },
        )
        Spacer(Modifier.height(12.dp))
        if (notes.isEmpty()) {
            Text("还没有速记。去录音页说一句吧。", color = VNColors.mist)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = note
                            }
                            false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(VNColors.danger, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.White)
                                Text(" 删除", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        },
                    ) {
                        NoteRow(
                            note = note,
                            onClick = { onOpen(note.id) },
                            onShare = { NoteExporter.share(context, deps.paths, note) },
                            onDelete = { pendingDelete = note },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除笔记？") },
            text = { Text("将删除「${note.displayTitle}」及其本地音频，不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            deps.pipeline.deleteNote(note.id)
                            pendingDelete = null
                        }
                    },
                ) {
                    Text("删除", color = VNColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val df = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(note.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(
                "${df.format(Date(note.createdAt))} · ${note.status.displayName}",
                style = MaterialTheme.typography.labelMedium,
                color = VNColors.statusColor(note.status),
            )
            note.summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = VNColors.mist, maxLines = 2)
            }
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = "导出分享")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = VNColors.danger)
        }
    }
}
