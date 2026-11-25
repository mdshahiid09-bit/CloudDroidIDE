package com.clouddroidide.android

import android.app.Application
import com.clouddroidide.android.di.AppContainer

class CloudDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        lateinit var container: AppContainer
            private set
    }
}
