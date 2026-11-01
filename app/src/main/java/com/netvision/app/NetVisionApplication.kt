package com.netvision.app

import android.app.Application
import com.netvision.app.database.AppDatabase
import com.netvision.app.repository.DeviceRepository
import com.netvision.app.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class NetVisionApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val deviceRepository by lazy { DeviceRepository(database.deviceDao(), database.networkRangeDao(), database.deviceFolderDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }

    companion object {
        private val _foregroundEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val foregroundEvents = _foregroundEvents.asSharedFlow()
        fun notifyAppForegrounded() { _foregroundEvents.tryEmit(Unit) }

        lateinit var instance: NetVisionApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
