package com.kriptikz.orehqmobile.data.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kriptikz.orehqmobile.data.daos.WalletDao
import com.kriptikz.orehqmobile.data.entities.Wallet
import kotlinx.coroutines.*

class WalletRepository(private val walletDao: WalletDao) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun insertWallet(wallet: Wallet) {
        coroutineScope.launch(Dispatchers.IO) {
            walletDao.insertWallet(wallet)
        }
    }

    suspend fun findWalletById(id: Int): List<Wallet>? {
        return walletDao.findWalletById(id)
    }

    suspend fun findWalletByPublicKey(publicKey: String): List<Wallet> {
        return walletDao.findWalletByPublicKey(publicKey)
    }

    fun getAllWallets(): List<Wallet> {
        return walletDao.getAllWallets()
    }

    fun deleteWallet(publicKey: String) {
        coroutineScope.launch(Dispatchers.IO) {
            walletDao.deleteWallet(publicKey)
        }
    }
}