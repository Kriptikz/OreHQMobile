package com.example.orehqmobile

import android.app.Application
import com.example.orehqmobile.data.AppContainer
import com.example.orehqmobile.data.DefaultAppContainer

class OreHQMobileApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}