package com.lingyou.voicenotes.infra.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lingyou.voicenotes.domain.NoteStatus
import org.json.JSONArray
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Path relative to app files Audio/ */
    val audioRelativePath: String? = null,
    val durationMs: Int = 0,
    val transcriptRaw: String? = null,
    val transcriptClean: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val bulletsJson: String? = null,
    val tagsJson: String? = null,
    val asrModelID: String? = null,
    val llmModelID: String? = null,
    val statusRaw: String = NoteStatus.DRAFT_AUDIO.raw,
) {
    val status: NoteStatus get() = NoteStatus.fromRaw(statusRaw)

    val bullets: List<String>
        get() = decodeStringArray(bulletsJson)

    val tags: List<String>
        get() = decodeStringArray(tagsJson)

    val displayTitle: String
        get() = when {
            !title.isNullOrBlank() -> title
            !transcriptRaw.isNullOrBlank() -> transcriptRaw.take(40)
            else -> "未命名速记"
        }

    companion object {
        fun encodeStringArray(value: List<String>): String? {
            if (value.isEmpty()) return null
            return JSONArray(value).toString()
        }

        fun decodeStringArray(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        add(arr.getString(i))
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
