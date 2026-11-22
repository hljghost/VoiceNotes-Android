package com.lingyou.voicenotes.domain

import com.lingyou.voicenotes.infra.persistence.AppPaths
import com.lingyou.voicenotes.infra.persistence.NoteDao
import com.lingyou.voicenotes.infra.persistence.NoteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Owns recording → ASR → enrich state machine.
 * Only one of ASR / LLM should be resident at a time.
 */
class NotePipeline(
    private val speechRecognizer: SpeechRecognizer,
    private val enricher: Enricher,
    private val noteDao: NoteDao,
    private val paths: AppPaths,
) {
    private val _activeNoteId = MutableStateFlow<String?>(null)
    val activeNoteId: StateFlow<String?> = _activeNoteId.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun createNote(audioFile: File, durationMs: Int): NoteEntity {
        val relative = audioFile.name
        // Ensure file lives under Audio/
        if (audioFile.parentFile?.absolutePath != paths.audioDir.absolutePath) {
            val dest = File(paths.audioDir, relative)
            audioFile.copyTo(dest, overwrite = true)
            audioFile.delete()
        }
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            audioRelativePath = relative,
            durationMs = durationMs,
            statusRaw = NoteStatus.DRAFT_AUDIO.raw,
        )
        noteDao.upsert(note)
        return note
    }

    suspend fun process(noteId: String) {
        _activeNoteId.value = noteId
        _lastError.value = null
        try {
            transcribe(noteId)
            enrich(noteId)
        } catch (e: Exception) {
            _lastError.value = e.message
        } finally {
            _activeNoteId.value = null
        }
    }

    suspend fun retryAsr(noteId: String) {
        _activeNoteId.value = noteId
        _lastError.value = null
        try {
            transcribe(noteId)
            enrich(noteId)
        } catch (e: Exception) {
            _lastError.value = e.message
        } finally {
            _activeNoteId.value = null
        }
    }

    suspend fun retryEnrich(noteId: String) {
        _activeNoteId.value = noteId
        _lastError.value = null
        try {
            enrich(noteId)
        } catch (e: Exception) {
            _lastError.value = e.message
        } finally {
            _activeNoteId.value = null
        }
    }

    suspend fun deleteNote(noteId: String) {
        if (_activeNoteId.value == noteId) _activeNoteId.value = null
        val note = noteDao.getById(noteId) ?: return
        note.audioRelativePath?.let { rel ->
            paths.audioFile(rel).delete()
        }
        noteDao.deleteById(noteId)
    }

    suspend fun updateEditable(
        noteId: String,
        title: String?,
        summary: String?,
        transcriptRaw: String?,
        bullets: List<String>?,
    ) {
        val note = noteDao.getById(noteId) ?: return
        noteDao.update(
            note.copy(
                title = title ?: note.title,
                summary = summary ?: note.summary,
                transcriptRaw = transcriptRaw ?: note.transcriptRaw,
                bulletsJson = bullets?.let { NoteEntity.encodeStringArray(it) } ?: note.bulletsJson,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun transcribe(noteId: String) {
        val note = noteDao.getById(noteId) ?: return
        val relative = note.audioRelativePath
            ?: throw SpeechRecognizerException.UnsupportedAudio
        val file = paths.audioFile(relative)
        noteDao.update(note.copy(statusRaw = NoteStatus.TRANSCRIBING.raw, updatedAt = System.currentTimeMillis()))
        try {
            enricher.unload()
            val text = speechRecognizer.transcribe(file)
            val latest = noteDao.getById(noteId) ?: return
            noteDao.update(
                latest.copy(
                    transcriptRaw = text,
                    asrModelID = speechRecognizer.modelID,
                    statusRaw = NoteStatus.TRANSCRIBED.raw,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            speechRecognizer.unload()
        } catch (e: Exception) {
            val latest = noteDao.getById(noteId)
            if (latest != null) {
                noteDao.update(
                    latest.copy(
                        statusRaw = NoteStatus.FAILED_ASR.raw,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            throw e
        }
    }

    private suspend fun enrich(noteId: String) {
        val note = noteDao.getById(noteId) ?: return
        val transcript = note.transcriptRaw
        if (transcript.isNullOrBlank()) throw EnricherException.EmptyTranscript
        noteDao.update(note.copy(statusRaw = NoteStatus.ENRICHING.raw, updatedAt = System.currentTimeMillis()))
        try {
            speechRecognizer.unload()
            val result = enricher.enrich(transcript)
            val latest = noteDao.getById(noteId) ?: return
            noteDao.update(
                latest.copy(
                    title = result.title,
                    summary = result.summary,
                    bulletsJson = NoteEntity.encodeStringArray(result.bullets),
                    tagsJson = NoteEntity.encodeStringArray(result.tags),
                    transcriptClean = result.transcriptClean ?: latest.transcriptClean,
                    llmModelID = result.modelID,
                    statusRaw = NoteStatus.READY.raw,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            enricher.unload()
        } catch (e: Exception) {
            val latest = noteDao.getById(noteId)
            if (latest != null) {
                noteDao.update(
                    latest.copy(
                        statusRaw = NoteStatus.FAILED_ENRICH.raw,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            throw e
        }
    }
}
