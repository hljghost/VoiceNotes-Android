package com.lingyou.voicenotes.app

import android.content.Context
import com.lingyou.voicenotes.domain.NotePipeline
import com.lingyou.voicenotes.infra.asr.SenseVoiceRecognizer
import com.lingyou.voicenotes.infra.audio.AudioPlayer
import com.lingyou.voicenotes.infra.audio.AudioRecorder
import com.lingyou.voicenotes.infra.llm.OnDeviceEnricher
import com.lingyou.voicenotes.infra.modelstore.ModelStore
import com.lingyou.voicenotes.infra.persistence.AppDatabase
import com.lingyou.voicenotes.infra.persistence.AppPaths
import com.lingyou.voicenotes.infra.persistence.NoteDao

class AppDependencies(context: Context) {
    val paths = AppPaths(context)
    val db: AppDatabase = AppDatabase.get(context)
    val noteDao: NoteDao = db.noteDao()
    val modelStore = ModelStore(asrRoot = paths.asrDir, llmRoot = paths.llmDir)
    val speechRecognizer = SenseVoiceRecognizer(asrRoot = paths.asrDir)
    val enricher = OnDeviceEnricher(appContext = context.applicationContext, llmRoot = paths.llmDir)
    val pipeline = NotePipeline(
        speechRecognizer = speechRecognizer,
        enricher = enricher,
        noteDao = noteDao,
        paths = paths,
    )
    val audioRecorder = AudioRecorder()
    val audioPlayer = AudioPlayer()
}
