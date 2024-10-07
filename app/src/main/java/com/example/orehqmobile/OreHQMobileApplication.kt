package com.example.orehqmobile

import android.app.Application
import com.example.orehqmobile.data.AppContainer
import com.example.orehqmobile.data.DefaultAppContainer
import com.example.orehqmobile.data.database.AppRoomDatabase

class OreHQMobileApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        val appDb = AppRoomDatabase.getInstance(this)
        container = DefaultAppContainer(this, appDb)
    }
}