package com.example.orehqmobile.data

import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.data.repositories.PoolRepository
import com.example.orehqmobile.data.repositories.SolanaRepository

interface AppContainer {
    val solanaRepository: ISolanaRepository
    val poolRepository: IPoolRepository
}

class DefaultAppContainer: AppContainer {
    override val solanaRepository: ISolanaRepository by lazy {
        SolanaRepository()
    }
    override val poolRepository: IPoolRepository by lazy {
        PoolRepository()
    }
}