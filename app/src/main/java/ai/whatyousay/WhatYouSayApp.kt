package ai.whatyousay

import android.app.Application

/** Holds the process-wide [AppContainer] so the screens share one set of dependencies. */
class WhatYouSayApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
