package com.example.orehqmobile.data

import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.data.repositories.SolanaRepository

interface AppContainer {
    val solanaRepository: ISolanaRepository
}

class DefaultAppContainer: AppContainer {
    override val solanaRepository: ISolanaRepository by lazy {
        SolanaRepository()
    }
}