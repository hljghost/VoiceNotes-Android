package com.lingyou.voicenotes.infra.audio

import android.media.MediaPlayer
import java.io.File

class AudioPlayer {
    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player?.isPlaying == true

    fun play(file: File, onCompletion: (() -> Unit)? = null) {
        stop()
        if (!file.exists()) return
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.setOnCompletionListener {
            onCompletion?.invoke()
            stop()
        }
        mp.prepare()
        mp.start()
        player = mp
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }
}
