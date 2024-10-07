package com.kriptikz.orehqmobile

import android.app.Application
import com.kriptikz.orehqmobile.data.AppContainer
import com.kriptikz.orehqmobile.data.DefaultAppContainer
import com.kriptikz.orehqmobile.data.database.AppRoomDatabase

class OreHQMobileApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        val appDb = AppRoomDatabase.getInstance(this)
        container = DefaultAppContainer(this, appDb)
    }
}