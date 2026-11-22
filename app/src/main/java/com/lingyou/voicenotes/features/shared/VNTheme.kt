package com.lingyou.voicenotes.features.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lingyou.voicenotes.domain.NoteStatus

object VNColors {
    val pine = Color(0xFF2E8C61)
    val pineDark = Color(0xFF1F6B48)
    val ink = Color(0xFF1A1C1A)
    val mist = Color(0xFF5C635E)
    val hush = Color(0xFF8A918C)
    val surface = Color(0xFFF3F6F3)
    val surfaceDark = Color(0xFF121412)
    val danger = Color(0xFFB83833)
    val working = Color(0xFFC77A1F)
    val ready = Color(0xFF2E8C61)
    val draft = Color(0xFF5C635E)

    fun statusColor(status: NoteStatus): Color = when (status) {
        NoteStatus.READY -> ready
        NoteStatus.FAILED_ASR, NoteStatus.FAILED_ENRICH -> danger
        NoteStatus.TRANSCRIBING, NoteStatus.ENRICHING -> working
        NoteStatus.DRAFT_AUDIO, NoteStatus.TRANSCRIBED -> draft
    }
}

private val LightColors = lightColorScheme(
    primary = VNColors.pine,
    onPrimary = Color.White,
    secondary = VNColors.pineDark,
    background = VNColors.surface,
    surface = Color.White,
    onBackground = VNColors.ink,
    onSurface = VNColors.ink,
    error = VNColors.danger,
)

private val DarkColors = darkColorScheme(
    primary = VNColors.pine,
    onPrimary = Color.White,
    secondary = VNColors.pine,
    background = VNColors.surfaceDark,
    surface = Color(0xFF1C1F1C),
    onBackground = Color(0xFFE8EDE9),
    onSurface = Color(0xFFE8EDE9),
    error = VNColors.danger,
)

@Composable
fun VoiceNotesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
