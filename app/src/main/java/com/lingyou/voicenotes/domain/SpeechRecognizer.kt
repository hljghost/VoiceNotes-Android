package com.lingyou.voicenotes.domain

import java.io.File

/** Offline ASR contract. MVP: sherpa-onnx SenseVoice. */
interface SpeechRecognizer {
    val modelID: String
    suspend fun isModelReady(): Boolean
    /** Prefer 16 kHz mono WAV/M4A/PCM. */
    suspend fun transcribe(audioFile: File): String
    /** Unload weights so LLM can load without OOM. */
    suspend fun unload()
}

sealed class SpeechRecognizerException(message: String) : Exception(message) {
    data object ModelNotReady : SpeechRecognizerException("ASR 模型尚未下载或不可用")
    data object UnsupportedAudio : SpeechRecognizerException("不支持的音频格式")
    class EngineFailure(detail: String) : SpeechRecognizerException("ASR 失败：$detail")
    data object Cancelled : SpeechRecognizerException("转写已取消")
}
