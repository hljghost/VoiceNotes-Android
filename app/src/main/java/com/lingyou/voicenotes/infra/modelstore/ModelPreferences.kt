package com.lingyou.voicenotes.infra.modelstore

import android.content.Context

/**
 * Active on-device model selection (Locally-style「使用 / 已选」).
 * MVP catalog is fixed: SenseVoice ASR + Qwen3.5-2B enricher.
 */
class ModelPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var activeAsrModelId: String
        get() = prefs.getString(KEY_ASR, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_ASR, value).apply()
        }

    var activeLlmModelId: String
        get() = prefs.getString(KEY_LLM, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LLM, value).apply()
        }

    fun clearAsrIf(modelId: String) {
        if (activeAsrModelId == modelId) activeAsrModelId = ""
    }

    fun clearLlmIf(modelId: String) {
        if (activeLlmModelId == modelId) activeLlmModelId = ""
    }

    companion object {
        private const val PREFS = "voicenotes_model_prefs"
        private const val KEY_ASR = "active_asr_model_id"
        private const val KEY_LLM = "active_llm_model_id"
    }
}
