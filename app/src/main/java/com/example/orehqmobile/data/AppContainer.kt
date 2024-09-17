package com.example.orehqmobile.data

import android.content.Context
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.data.repositories.KeypairRepository
import com.example.orehqmobile.data.repositories.PoolRepository
import com.example.orehqmobile.data.repositories.SolanaRepository

interface AppContainer {
    val solanaRepository: ISolanaRepository
    val poolRepository: IPoolRepository
    val keypairRepository: IKeypairRepository
}

class DefaultAppContainer(private val context: Context): AppContainer {
    override val solanaRepository: ISolanaRepository by lazy {
        SolanaRepository()
    }
    override val poolRepository: IPoolRepository by lazy {
        PoolRepository()
    }
    override val keypairRepository: IKeypairRepository by lazy {
        KeypairRepository(context)
    }
}