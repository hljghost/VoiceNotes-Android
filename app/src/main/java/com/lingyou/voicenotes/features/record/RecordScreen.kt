package com.lingyou.voicenotes.features.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lingyou.voicenotes.app.AppDependencies
import com.lingyou.voicenotes.features.shared.VNColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecordScreen(
    deps: AppDependencies,
    onOpenNote: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("点按开始中文短速记") }
    var error by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording(deps) { recording = true; status = "录音中…"; error = null }
        } else {
            error = "需要麦克风权限"
        }
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (recording) {
            elapsedMs = System.currentTimeMillis() - start
            delay(200)
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "VoiceNotes",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = VNColors.pine,
        )
        Spacer(Modifier.height(8.dp))
        Text(status, color = VNColors.mist, style = MaterialTheme.typography.bodyMedium)
        if (recording) {
            Spacer(Modifier.height(12.dp))
            Text(formatMs(elapsedMs), style = MaterialTheme.typography.titleLarge)
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = VNColors.danger)
        }
        Spacer(Modifier.height(48.dp))
        Box(contentAlignment = Alignment.Center) {
            FloatingActionButton(
                onClick = {
                    if (recording) {
                        recording = false
                        status = "处理中…"
                        scope.launch {
                            val result = deps.audioRecorder.stop()
                            if (result == null) {
                                error = "录音太短"
                                status = "点按开始中文短速记"
                                return@launch
                            }
                            val (file, duration) = result
                            try {
                                val note = deps.pipeline.createNote(file, duration)
                                status = "转写与整理中…"
                                deps.pipeline.process(note.id)
                                status = "完成"
                                onOpenNote(note.id)
                            } catch (e: Exception) {
                                error = e.message
                                status = "点按开始中文短速记"
                            }
                        }
                    } else {
                        val ok = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (ok) {
                            startRecording(deps) {
                                recording = true
                                status = "录音中…"
                                error = null
                                elapsedMs = 0
                            }
                        } else {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier
                    .size(96.dp)
                    .scale(scale),
                containerColor = if (recording) VNColors.danger else VNColors.pine,
                shape = CircleShape,
            ) {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (recording) "停止" else "录音",
                    modifier = Modifier.size(40.dp),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "全程离线 · SenseVoice + 端侧整理",
            style = MaterialTheme.typography.labelMedium,
            color = VNColors.hush,
        )
    }
}

private fun startRecording(deps: AppDependencies, onStarted: () -> Unit) {
    deps.audioRecorder.start(deps.paths.audioDir)
    onStarted()
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
