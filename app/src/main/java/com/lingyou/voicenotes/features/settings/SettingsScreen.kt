package com.lingyou.voicenotes.features.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingyou.voicenotes.app.AppDependencies
import com.lingyou.voicenotes.features.shared.VNColors
import com.lingyou.voicenotes.infra.modelstore.QwenModelCatalog
import com.lingyou.voicenotes.infra.modelstore.SenseVoiceModelCatalog

@Composable
fun SettingsScreen(
    deps: AppDependencies,
    onOpenManageModels: () -> Unit,
) {
    var asrReady by remember { mutableStateOf(false) }
    var llmReady by remember { mutableStateOf(false) }
    var activeAsr by remember { mutableStateOf("") }
    var activeLlm by remember { mutableStateOf("") }

    fun refresh() {
        asrReady = deps.modelStore.asrReady()
        llmReady = deps.modelStore.llmReady()
        activeAsr = deps.modelPrefs.activeAsrModelId
        activeLlm = deps.modelPrefs.activeLlmModelId
    }

    // Re-enters composition after pop from 管理模型.
    LaunchedEffect(Unit) { refresh() }

    val asrLabel = when {
        asrReady && activeAsr == SenseVoiceModelCatalog.MODEL_ID -> "SenseVoice · 已就绪"
        asrReady -> "SenseVoice · 已安装未选用"
        else -> "SenseVoice · 未安装"
    }
    val llmLabel = when {
        llmReady && activeLlm == QwenModelCatalog.MODEL_ID -> "Qwen3.5-2B · 已就绪"
        llmReady -> "Qwen3.5-2B · 已安装未选用"
        else -> "Qwen3.5-2B · 未安装（启发式）"
    }

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

        Text("模型", fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenManageModels)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("管理模型", fontWeight = FontWeight.SemiBold)
                Text(
                    "$asrLabel\n$llmLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = VNColors.mist,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VNColors.hush,
            )
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
