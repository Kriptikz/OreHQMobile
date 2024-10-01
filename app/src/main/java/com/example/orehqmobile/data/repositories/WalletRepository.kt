package com.example.orehqmobile.data.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.orehqmobile.data.daos.WalletDao
import com.example.orehqmobile.data.entities.Wallet
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

    suspend fun getAllWallets(): List<Wallet> {
        return walletDao.getAllWallets()
    }

    fun deleteWallet(publicKey: String) {
        coroutineScope.launch(Dispatchers.IO) {
            walletDao.deleteWallet(publicKey)
        }
    }
}