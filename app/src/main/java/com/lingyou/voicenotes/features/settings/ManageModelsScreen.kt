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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingyou.voicenotes.app.AppDependencies
import com.lingyou.voicenotes.features.shared.VNColors
import com.lingyou.voicenotes.infra.modelstore.ModelKind
import com.lingyou.voicenotes.infra.modelstore.QwenModelCatalog
import com.lingyou.voicenotes.infra.modelstore.SenseVoiceModelCatalog
import com.lingyou.voicenotes.infra.modelstore.formatBytes
import kotlinx.coroutines.launch

private enum class ModelUiStatus { Available, Downloading, Installed, Failed }

@Composable
fun ManageModelsScreen(
    deps: AppDependencies,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val busy by deps.modelStore.busy.collectAsState()
    val progress by deps.modelStore.progress.collectAsState()
    val lastError by deps.modelStore.lastError.collectAsState()

    var asrReady by remember { mutableStateOf(false) }
    var llmReady by remember { mutableStateOf(false) }
    var asrBytes by remember { mutableStateOf(0L) }
    var llmBytes by remember { mutableStateOf(0L) }
    var activeAsr by remember { mutableStateOf(deps.modelPrefs.activeAsrModelId) }
    var activeLlm by remember { mutableStateOf(deps.modelPrefs.activeLlmModelId) }
    var asrFailed by remember { mutableStateOf(false) }
    var llmFailed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        asrReady = deps.modelStore.asrReady()
        llmReady = deps.modelStore.llmReady()
        asrBytes = deps.modelStore.asrBytes()
        llmBytes = deps.modelStore.llmBytes()
        activeAsr = deps.modelPrefs.activeAsrModelId
        activeLlm = deps.modelPrefs.activeLlmModelId
        // Auto-heal selection when weights already on disk (e.g. side-load).
        if (asrReady && activeAsr.isEmpty()) {
            deps.modelPrefs.activeAsrModelId = SenseVoiceModelCatalog.MODEL_ID
            activeAsr = SenseVoiceModelCatalog.MODEL_ID
        }
        if (llmReady && activeLlm.isEmpty()) {
            deps.modelPrefs.activeLlmModelId = QwenModelCatalog.MODEL_ID
            activeLlm = QwenModelCatalog.MODEL_ID
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(busy) {
        if (!busy) refresh()
    }

    val asrStatus = when {
        busy && progress?.kind == ModelKind.ASR -> ModelUiStatus.Downloading
        asrFailed && !asrReady -> ModelUiStatus.Failed
        asrReady -> ModelUiStatus.Installed
        else -> ModelUiStatus.Available
    }
    val llmStatus = when {
        busy && progress?.kind == ModelKind.LLM -> ModelUiStatus.Downloading
        llmFailed && !llmReady -> ModelUiStatus.Failed
        llmReady -> ModelUiStatus.Installed
        else -> ModelUiStatus.Available
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "管理模型",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        }

        Text(
            "从 ModelScope（内地）下载到本机。首次需联网，之后可离线录音与整理。",
            style = MaterialTheme.typography.bodySmall,
            color = VNColors.mist,
        )

        (message ?: lastError)?.let { err ->
            Text(
                err,
                color = if (lastError != null && message == null) VNColors.danger else VNColors.pine,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("模型", fontWeight = FontWeight.SemiBold, color = VNColors.mist)

        ManagedModelCard(
            name = "SenseVoice ASR",
            summary = "sherpa-onnx · 中英粤日韩转写",
            modelId = SenseVoiceModelCatalog.MODEL_ID,
            approximateSizeLabel = "约 0.23 GB",
            status = asrStatus,
            bytesOnDisk = asrBytes,
            selected = activeAsr == SenseVoiceModelCatalog.MODEL_ID && asrReady,
            busy = busy,
            progress = progress?.takeIf { it.kind == ModelKind.ASR },
            onUse = {
                deps.modelPrefs.activeAsrModelId = SenseVoiceModelCatalog.MODEL_ID
                activeAsr = SenseVoiceModelCatalog.MODEL_ID
                message = "已选用 SenseVoice"
            },
            onInstall = {
                scope.launch {
                    message = null
                    asrFailed = false
                    try {
                        deps.modelStore.downloadSenseVoice()
                        deps.modelPrefs.activeAsrModelId = SenseVoiceModelCatalog.MODEL_ID
                        message = "SenseVoice 下载完成"
                    } catch (e: Exception) {
                        asrFailed = true
                        message = e.message
                    }
                    refresh()
                }
            },
            onUninstall = {
                scope.launch {
                    deps.speechRecognizer.unload()
                    deps.modelStore.deleteSenseVoice()
                    deps.modelPrefs.clearAsrIf(SenseVoiceModelCatalog.MODEL_ID)
                    message = "已卸载 SenseVoice"
                    refresh()
                }
            },
        )

        ManagedModelCard(
            name = "Qwen3.5-2B 整理",
            summary = "llama.cpp · 标题 / 摘要 / 要点",
            modelId = QwenModelCatalog.MODEL_ID,
            approximateSizeLabel = "约 1.28 GB",
            status = llmStatus,
            bytesOnDisk = llmBytes,
            selected = activeLlm == QwenModelCatalog.MODEL_ID && llmReady,
            busy = busy,
            progress = progress?.takeIf { it.kind == ModelKind.LLM },
            onUse = {
                deps.modelPrefs.activeLlmModelId = QwenModelCatalog.MODEL_ID
                activeLlm = QwenModelCatalog.MODEL_ID
                message = "已选用 Qwen3.5-2B"
            },
            onInstall = {
                scope.launch {
                    message = null
                    llmFailed = false
                    try {
                        deps.modelStore.downloadQwen()
                        deps.modelPrefs.activeLlmModelId = QwenModelCatalog.MODEL_ID
                        message = "Qwen3.5-2B 下载完成"
                    } catch (e: Exception) {
                        llmFailed = true
                        message = e.message
                    }
                    refresh()
                }
            },
            onUninstall = {
                scope.launch {
                    deps.enricher.unload()
                    deps.modelStore.deleteQwen()
                    deps.modelPrefs.clearLlmIf(QwenModelCatalog.MODEL_ID)
                    message = "已卸载 Qwen3.5-2B"
                    refresh()
                }
            },
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "未安装整理模型时，笔记仍可用启发式标题/摘要（标注 …-heuristic）。",
            style = MaterialTheme.typography.labelSmall,
            color = VNColors.hush,
        )
    }
}

@Composable
private fun ManagedModelCard(
    name: String,
    summary: String,
    modelId: String,
    approximateSizeLabel: String,
    status: ModelUiStatus,
    bytesOnDisk: Long,
    selected: Boolean,
    busy: Boolean,
    progress: com.lingyou.voicenotes.infra.modelstore.DownloadProgress?,
    onUse: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "已选",
                    tint = VNColors.pine,
                )
            }
        }
        Text(text = summary, style = MaterialTheme.typography.bodySmall, color = VNColors.mist)
        Text(
            text = modelId,
            style = MaterialTheme.typography.labelSmall,
            color = VNColors.hush,
        )

        when (status) {
            ModelUiStatus.Available -> Text(
                text = "未安装 · $approximateSizeLabel",
                style = MaterialTheme.typography.labelMedium,
                color = VNColors.working,
            )
            ModelUiStatus.Downloading -> {
                val p = progress
                val frac = if (p?.bytesTotal != null && p.bytesTotal > 0) {
                    (p.bytesReceived.toFloat() / p.bytesTotal).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Text(
                    text = p?.message ?: "下载中…",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (frac > 0f) {
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "${(frac * 100).toInt()}% · ${p?.bytesReceived?.formatBytes() ?: ""}" +
                            (p?.bytesTotal?.let { " / ${it.formatBytes()}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = VNColors.hush,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            ModelUiStatus.Installed -> Text(
                text = "已就绪 · 占用 ${bytesOnDisk.formatBytes()}",
                style = MaterialTheme.typography.labelMedium,
                color = VNColors.ready,
            )
            ModelUiStatus.Failed -> Text(
                text = "安装失败 — 检查网络/磁盘后重试（仅 ModelScope）。",
                style = MaterialTheme.typography.labelMedium,
                color = VNColors.danger,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onUse,
                enabled = status == ModelUiStatus.Installed,
            ) {
                Text(if (selected) "已选" else "使用")
            }

            if (status == ModelUiStatus.Available || status == ModelUiStatus.Failed) {
                Button(onClick = onInstall, enabled = !busy) {
                    Text(if (status == ModelUiStatus.Failed) "重试安装" else "安装")
                }
            }

            if (status == ModelUiStatus.Installed || bytesOnDisk > 0) {
                TextButton(
                    onClick = onUninstall,
                    enabled = !busy,
                ) {
                    Text("卸载", color = VNColors.danger)
                }
            }

            if (status == ModelUiStatus.Installed) {
                TextButton(onClick = onInstall, enabled = !busy) {
                    Text("重新下载")
                }
            }
        }
    }
}
