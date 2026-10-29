package com.arya.companion

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow

class AryaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** True while MainActivity is on screen. Android only lets us start other apps from the foreground. */
object AppVisibility {
    val visible = MutableStateFlow(false)
}
