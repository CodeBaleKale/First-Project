package com.snapcal.app

import android.app.Application
import com.snapcal.app.data.ItemRepository
import com.snapcal.app.data.SettingsStore

class SnapCalApp : Application() {
    lateinit var repository: ItemRepository
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ItemRepository(this)
        settings = SettingsStore(this)
    }
}
