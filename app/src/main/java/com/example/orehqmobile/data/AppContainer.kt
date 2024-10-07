package com.example.orehqmobile.data

import android.content.Context
import com.example.orehqmobile.data.database.AppRoomDatabase
import com.example.orehqmobile.data.repositories.IKeypairRepository
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.ISolanaRepository
import com.example.orehqmobile.data.repositories.KeypairRepository
import com.example.orehqmobile.data.repositories.PoolRepository
import com.example.orehqmobile.data.repositories.SolanaRepository
import com.example.orehqmobile.data.repositories.SubmissionResultRepository
import com.example.orehqmobile.data.repositories.WalletRepository

interface AppContainer {
    val solanaRepository: ISolanaRepository
    val poolRepository: IPoolRepository
    val keypairRepository: IKeypairRepository
    val walletRepository: WalletRepository
    val submissionResultRepository: SubmissionResultRepository
}

class DefaultAppContainer(private val context: Context, private val appDb: AppRoomDatabase): AppContainer {
    override val solanaRepository: ISolanaRepository by lazy {
        SolanaRepository()
    }
    override val poolRepository: IPoolRepository by lazy {
        PoolRepository()
    }
    override val keypairRepository: IKeypairRepository by lazy {
        KeypairRepository(context)
    }

    override val walletRepository: WalletRepository by lazy {
        val appDb = AppRoomDatabase.getInstance(context)
        val walletDao = appDb.walletDao()
        WalletRepository(walletDao)
    }

    override val submissionResultRepository: SubmissionResultRepository by lazy {
        val appDb = AppRoomDatabase.getInstance(context)
        val submissionResultDao = appDb.submissionResultDao()
        SubmissionResultRepository(submissionResultDao)
    }
}