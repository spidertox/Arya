package com.arya.companion

import android.app.Application
import com.arya.companion.data.MemoryStore
import com.arya.companion.data.SettingsStore
import com.arya.companion.live.AryaEngine

/** Tiny service locator: the app has a handful of process-wide singletons and no DI framework. */
object Graph {
    lateinit var app: Application
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var memories: MemoryStore
        private set

    fun init(application: Application) {
        app = application
        settings = SettingsStore(application)
        memories = MemoryStore(application)
        AryaEngine.init(application)
    }
}
