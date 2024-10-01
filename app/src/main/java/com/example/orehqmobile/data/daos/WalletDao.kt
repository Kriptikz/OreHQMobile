package com.example.orehqmobile.data.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.orehqmobile.data.entities.Wallet

@Dao
interface WalletDao {
    @Insert
    fun insertWallet(wallet: Wallet)

    @Query("DELETE FROM wallets WHERE publicKey = :publicKey")
    fun deleteWallet(publicKey: String)

    @Query("SELECT * FROM wallets WHERE publicKey = :publicKey")
    fun findWallet(publicKey: String): List<Wallet>

    @Query("SELECT * FROM wallets WHERE walletId = :id")
    fun findWalletById(id: Int): List<Wallet>

    @Query("SELECT * FROM wallets WHERE publicKey = :publicKey")
    fun findWalletByPublicKey(publicKey: String): List<Wallet>

    @Query("SELECT * FROM wallets")
    fun getAllWallets(): List<Wallet>
}