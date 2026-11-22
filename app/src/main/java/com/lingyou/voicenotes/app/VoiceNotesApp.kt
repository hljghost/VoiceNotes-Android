package com.lingyou.voicenotes.app

import android.app.Application

class VoiceNotesApp : Application() {
    lateinit var deps: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        deps = AppDependencies(this)
    }
}

val ContextDeps: (android.content.Context) -> AppDependencies = { ctx ->
    (ctx.applicationContext as VoiceNotesApp).deps
}
