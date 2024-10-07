package com.kriptikz.orehqmobile.data

import android.content.Context
import com.kriptikz.orehqmobile.data.database.AppRoomDatabase
import com.kriptikz.orehqmobile.data.repositories.AppAccountRepository
import com.kriptikz.orehqmobile.data.repositories.IKeypairRepository
import com.kriptikz.orehqmobile.data.repositories.IPoolRepository
import com.kriptikz.orehqmobile.data.repositories.ISolanaRepository
import com.kriptikz.orehqmobile.data.repositories.KeypairRepository
import com.kriptikz.orehqmobile.data.repositories.PoolRepository
import com.kriptikz.orehqmobile.data.repositories.SolanaRepository
import com.kriptikz.orehqmobile.data.repositories.SubmissionResultRepository
import com.kriptikz.orehqmobile.data.repositories.WalletRepository

interface AppContainer {
    val solanaRepository: ISolanaRepository
    val poolRepository: IPoolRepository
    val keypairRepository: IKeypairRepository
    val walletRepository: WalletRepository
    val submissionResultRepository: SubmissionResultRepository
    val appAccountRepository: AppAccountRepository
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

    override val appAccountRepository: AppAccountRepository by lazy {
        val appDb = AppRoomDatabase.getInstance(context)
        val appAccountDao = appDb.appAccountDao()
        AppAccountRepository(appAccountDao)
    }
}