package com.lingyou.voicenotes.features.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingyou.voicenotes.app.AppDependencies
import com.lingyou.voicenotes.features.shared.VNColors
import com.lingyou.voicenotes.infra.modelstore.QwenModelCatalog
import com.lingyou.voicenotes.infra.modelstore.SenseVoiceModelCatalog
import com.lingyou.voicenotes.infra.modelstore.formatBytes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(deps: AppDependencies) {
    val scope = rememberCoroutineScope()
    val busy by deps.modelStore.busy.collectAsState()
    val progress by deps.modelStore.progress.collectAsState()
    val lastError by deps.modelStore.lastError.collectAsState()
    var asrReady by remember { mutableStateOf(false) }
    var llmReady by remember { mutableStateOf(false) }
    var asrBytes by remember { mutableStateOf(0L) }
    var llmBytes by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        asrReady = deps.modelStore.asrReady()
        llmReady = deps.modelStore.llmReady()
        asrBytes = deps.modelStore.asrBytes()
        llmBytes = deps.modelStore.llmBytes()
    }

    LaunchedEffect(busy) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text(
            "数据仅存本机。ASR / 整理模型按需从 ModelScope（内地）下载。音频、转写与整理结果默认永不上传。",
            color = VNColors.mist,
            style = MaterialTheme.typography.bodyMedium,
        )

        ModelCard(
            title = "SenseVoice ASR",
            subtitle = "sherpa-onnx · ${SenseVoiceModelCatalog.MODEL_ID}\n源：${SenseVoiceModelCatalog.MIRROR_LABEL}",
            ready = asrReady,
            bytes = asrBytes,
            busy = busy,
            onDownload = {
                scope.launch {
                    message = null
                    try {
                        deps.modelStore.downloadSenseVoice()
                        message = "SenseVoice 下载完成"
                    } catch (e: Exception) {
                        message = e.message
                    }
                    refresh()
                }
            },
            onDelete = {
                scope.launch {
                    deps.modelStore.deleteSenseVoice()
                    message = "已删除 SenseVoice"
                    refresh()
                }
            },
        )

        ModelCard(
            title = "端侧整理 LLM",
            subtitle = buildString {
                append("Qwen3.5-2B Q4_K_M · ${QwenModelCatalog.MODEL_ID}\n")
                append("运行时：${QwenModelCatalog.RUNTIME_LABEL}\n")
                append("源：${QwenModelCatalog.MIRROR_LABEL} · ~1.28 GB\n")
                append("未下载时使用启发式整理（llmModelID=…-heuristic）")
            },
            ready = llmReady,
            bytes = llmBytes,
            busy = busy,
            onDownload = {
                scope.launch {
                    message = null
                    try {
                        deps.modelStore.downloadQwen()
                        message = "Qwen3.5-2B 下载完成"
                    } catch (e: Exception) {
                        message = e.message
                    }
                    refresh()
                }
            },
            onDelete = {
                scope.launch {
                    deps.modelStore.deleteQwen()
                    message = "已删除 Qwen3.5-2B"
                    refresh()
                }
            },
        )

        if (busy && progress != null) {
            val p = progress!!
            Text(p.message, style = MaterialTheme.typography.labelMedium)
            val frac = if (p.bytesTotal != null && p.bytesTotal > 0) {
                (p.bytesReceived.toFloat() / p.bytesTotal).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (frac > 0f) {
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                "${p.bytesReceived.formatBytes()}" +
                    (p.bytesTotal?.let { " / ${it.formatBytes()}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = VNColors.hush,
            )
        }

        (message ?: lastError)?.let {
            Text(it, color = if (lastError != null && message == null) VNColors.danger else VNColors.pine)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("整理引擎", fontWeight = FontWeight.SemiBold)
            Text("本机 Qwen3.5-2B（llama.cpp）", color = VNColors.ink)
        }
        Text(
            "MVP 唯一选项；云端 Enricher 接口已预留，未接线。",
            color = VNColors.mist,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))
        Text("关于", fontWeight = FontWeight.SemiBold)
        Text(
            "VoiceNotes Android MVP · 录音 → SenseVoice → Qwen3.5-2B 整理 → Room\n" +
                "包名 com.lingyou.voicenotes · 中文短速记",
            style = MaterialTheme.typography.bodySmall,
            color = VNColors.hush,
        )
    }
}

@Composable
private fun ModelCard(
    title: String,
    subtitle: String,
    ready: Boolean,
    bytes: Long,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VNColors.mist)
        Text(
            if (ready) "已就绪 · 占用 ${bytes.formatBytes()}" else "未下载 · 占用 ${bytes.formatBytes()}",
            color = if (ready) VNColors.ready else VNColors.working,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDownload, enabled = !busy) {
                Text(if (ready) "重新下载" else "从 ModelScope 下载")
            }
            OutlinedButton(onClick = onDelete, enabled = !busy && bytes > 0) {
                Text("删除")
            }
        }
    }
}
