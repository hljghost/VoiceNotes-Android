package com.lingyou.voicenotes.domain

/** Pipeline status. Failures never discard prior artifacts. */
enum class NoteStatus(val raw: String, val displayName: String) {
    DRAFT_AUDIO("draftAudio", "待转写"),
    TRANSCRIBING("transcribing", "转写中"),
    TRANSCRIBED("transcribed", "已转写"),
    ENRICHING("enriching", "整理中"),
    READY("ready", "已完成"),
    FAILED_ASR("failedASR", "转写失败"),
    FAILED_ENRICH("failedEnrich", "整理失败");

    val isProcessing: Boolean
        get() = this == TRANSCRIBING || this == ENRICHING

    val canRetryAsr: Boolean
        get() = this == FAILED_ASR || this == DRAFT_AUDIO

    val canRetryEnrich: Boolean
        get() = this == FAILED_ENRICH || this == TRANSCRIBED

    companion object {
        fun fromRaw(raw: String?): NoteStatus =
            entries.firstOrNull { it.raw == raw } ?: DRAFT_AUDIO
    }
}
