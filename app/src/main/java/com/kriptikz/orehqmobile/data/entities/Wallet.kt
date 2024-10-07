package com.kriptikz.orehqmobile.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
class Wallet {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "walletId")
    var id: Int = 0

    @ColumnInfo(name = "publicKey")
    var publicKey: String = ""
    var authToken: String = ""

    constructor()

    constructor(publicKey: String, authToken: String) {
        this.publicKey = publicKey
        this.authToken = authToken
    }
}